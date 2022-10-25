package toy;

import java.util.concurrent.LinkedBlockingDeque;

/**
 * Testing AsMonitor MT safe solution - the queue is filled by AsMonitor thread and AsMonitor separate
 * thread is used to empty (remove) work. THe difference is the queue uses AsMonitor
 * blocking take operation and is MT-Safe. The classes are:
 * 
 * Put --> AsQueue --> Take
 * 
 * @author gash
 *
 */
public class AsQueue {
	private boolean _verbose = false;
	private int _iter = 1;
	private LinkedBlockingDeque<Work> _queue;
	private Put _put;
	private Take _take;

	public void iterate(int times) {
		_iter = times;
		for (; _iter > 0; _iter--) {
			setup();
			runOnce();
			report();
		}
	}

	public void setup() {
		_queue = new LinkedBlockingDeque<Work>();
		_put = new Put(_queue, 100);
		_take = new Take(_queue, 100);
	}

	public void runOnce() {
		if (_verbose)
			System.out.println("--> starting test " + _iter);
		_put.start();
		_take.start();

		int maxWaiting = 50;
		while (_take._isRunning) {
			try {
				maxWaiting--;
				if (maxWaiting == 0) {
					System.out.println("--> terminated (timed out) test " + _iter);
					_take._isRunning = false;
				}
				if (_verbose)
					System.out.println("--> waiting: " + _queue.size());
				Thread.sleep(200);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public void report() {
		System.out.flush();
		String pass = (_put._sum == _take._sum) ? "Pass" : "Fail";
		System.out.println("Iter: " + _iter + ": " + pass + " (" + _put._sum + " = " + _take._sum + ")");
	}

	/**
	 * the work
	 * 
	 * @author gash
	 *
	 */
	public static final class Work {
		public int _id;
		public int _payload;

		public Work(int id, int payload) {
			_id = id;
			_payload = payload;
		}
	}

	/**
	 * puts work on the queue
	 * 
	 * @author gash
	 *
	 */
	public static final class Put extends Thread {
		public boolean _verbose = false;
		public int _genID = 0;
		public int _iter;
		public int _sum = 0;
		public boolean _isRunning = true;
		public LinkedBlockingDeque<Work> _q;

		public Put(LinkedBlockingDeque<Work> q, int iterations) {
			_q = q;
			_iter = iterations;
		}

		@Override
		public void run() {
			while (_isRunning && _genID <= _iter) {
				_genID++;
				_sum += _genID;
				if (_verbose && _genID % 10 == 0)
					System.out.println("---> putting " + _genID);
				_q.add(new Work(_genID, _genID));

				try {
					Thread.sleep(10); // simulate variability
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			_isRunning = false;
			if (_verbose)
				System.out.println("--> Put is done");
		}
	}

	/**
	 * takes work from the queue
	 * 
	 * @author gash
	 *
	 */
	public static final class Take extends Thread {
		public boolean _verbose = false;
		public int _iter = 0;
		public int _sum = 0;
		public boolean _isRunning = true;
		public LinkedBlockingDeque<Work> _q;

		public Take(LinkedBlockingDeque<Work> q, int iter) {
			_q = q;
			_iter = iter;
		}

		@Override
		public void run() {
			while (_isRunning && _iter >= 0) {
				try {
					Work w = _q.take(); // blocking
					_iter--;
					_sum += w._payload;
					if (_verbose && _sum % 10 == 0)
						System.out.println("taken: " + _iter);
					// we don't need to sleep!
				} catch (Exception e) {
					// ignore - part of the test
				}
			}
			_isRunning = false;
			if (_verbose)
				System.out.println("--> Take is done");
		}
	}

	/**
	 * run our test
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		AsQueue aa = new AsQueue();
		aa.iterate(20);
	}
}
