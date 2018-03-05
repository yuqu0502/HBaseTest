package hbasetest;

import io.memorymq.MemoryMQImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;

import parse.bean.MessageB0;

public class HBaseWrite {
	public static AtomicInteger counter = new AtomicInteger(0);
	public static Connection connection; // 一个数据库连接对象
	static {
		Configuration conf = HBaseConfiguration.create();
		conf.set("hbase.zookeeper.quorum", "192.168.159.170,192.168.159.171,192.168.159.172");
		conf.set("hbase.zookeeper.property.clientPort", "2181");
		try {
			connection = ConnectionFactory.createConnection(conf);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws Exception {
		MemoryMQImpl<MessageB0> mq = new MemoryMQImpl<>(10000);
		new Listener().start();
		for (int i = 0; i < 1; i++) {
			new HBaseWriterThread(mq).start();
			Thread.sleep(100); // 过1秒钟启动一个
		}
		while(true){
			mq.put(new MessageB0());
			Thread.sleep(1000);
		}
	}
}

class HBaseWriterThread extends Thread {
	private MemoryMQImpl<MessageB0> mq;

	public HBaseWriterThread(MemoryMQImpl<MessageB0> mq) {
		this.mq = mq;
	}

	@Override
	public void run() {
		List<MessageB0> list = new ArrayList<MessageB0>();
		boolean sendResult = true; // 上次发送的结果

		while (true) {
			MessageB0 msg = null;
			if (sendResult) { // 上次发送成功了,这次才读取消息,否则,不读取
				msg = mq.get();
			}
			if (msg == null) { // mq没了
				if (list.size() == 0) { // 桥中和本方法的list中都没有消息,就继续死循环
					this.sleepSafe(100);
					continue;
				} else { // 桥中没有消息,但是本方法的list中有,就发送
					boolean result = this.send(list);
					if (result) { // 如果发送成功
						list.clear();
						sendResult = true;
					} else {
						sendResult = false;
					}
				}
			} else { //
				list.add(msg);
				if (list.size() >= 20) {
					boolean result = this.send(list);
					if (result) {
						list.clear();
						sendResult = true;
					} else {
						sendResult = false;
					}
				}
			}

		}
	}

	private boolean send(List<MessageB0> msgList) {
		Random random = new Random();
		Table table = null;
		List<Put> putList = new ArrayList<Put>();
		try {
			table = HBaseWrite.connection.getTable(TableName.valueOf("t_book"));
			for (MessageB0 msg : msgList) {
				byte[] bytes = new byte[20];
				random.nextBytes(bytes);
				Put put = new Put(bytes);
				for (int j = 0; j < 30; j++) {
					put.addColumn(Bytes.toBytes("base"), Bytes.toBytes("name" + j), Bytes.toBytes("bbbb" + j));
				}
				put.addColumn(Bytes.toBytes("base"), Bytes.toBytes("r10"), Bytes.toBytes(msg.getR10()));
				putList.add(put);
			}

			HBaseWrite.counter.getAndAdd(msgList.size());
			table.put(putList);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		} finally {
			try {
				table.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

	private void sleepSafe(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}

class Listener extends Thread {
	int previousCounter = 0;

	@Override
	public void run() {
		while (true) {
			int counter = HBaseWrite.counter.intValue();
			System.out.println(counter - previousCounter);
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			previousCounter = counter;
		}
	}
}