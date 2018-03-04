package hbasetest;

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
		new Listener().start();
		for (int i = 0; i < 1; i++) {
			new HBaseWriterThread().start();
			Thread.sleep(100); // 过1秒钟启动一个
		}
	}
}

class HBaseWriterThread extends Thread {
	@Override
	public void run() {
		Random random = new Random();
		List<Put> list = new ArrayList<Put>();
		try {
			for (int i = 1;; i++) {
				Table table = HBaseWrite.connection.getTable(TableName.valueOf("t_book"));
				byte[] bytes = new byte[20];
				random.nextBytes(bytes);
				Put put = new Put(("row" + i).getBytes());
				for (int j = 0; j < 20; j++) {
					put.addColumn(Bytes.toBytes("base"), Bytes.toBytes("name" + j), Bytes.toBytes("bbbb" + j));
				}
				list.add(put);
				HBaseWrite.counter.incrementAndGet();
				if (i % 100 == 0) { // 每100个put会保存一次
					table.put(list);
					table.close();
					list.clear();
				}
			}
		} catch (Exception e) {
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