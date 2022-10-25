package toy;

import java.util.concurrent.LinkedBlockingDeque;

/**
 * Testing AsMonitor MT safe solution - the queue is filled by AsMonitor thread
 * and AsMonitor separate thread is used to empty (remove) work. THe difference
 * is the queue uses AsMonitor blocking take operation and is MT-Safe. The
 * classes are:
 * 
 * <pre>
 * Put --> AsQueue --> Take
 *  ^                    ^
 *   --------------------
 *           |
 *        Monitor
 * </pre>
 * 
 * @author gash
 *
 */
public class AsMonitor {
	private static final int sMaxWork = 10;

	private boolean _verbose = false;
	private int _iter = 1;
	private LinkedBlockingDeque<Work> _queue;
	private Put _put;
	private Take _take;
	private Monitor _monitor;

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
		_put = new Put(_queue);
		_take = new Take(_queue);
		_monitor = new Monitor(_put, _take, AsMonitor.sMaxWork);
	}

	public void runOnce() {
		if (_verbose)
			System.out.println("--> starting test " + _iter);
		_put.start();
		_take.start();
		_monitor.start();

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
		public int _sum = 0;
		public boolean _isRunning = true;
		public LinkedBlockingDeque<Work> _q;

		public Put(LinkedBlockingDeque<Work> q) {
			_q = q;
		}

		public void shutdown() {
			_isRunning = false;
		}

		@Override
		public void run() {
			while (_isRunning) {
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

		public Take(LinkedBlockingDeque<Work> q) {
			_q = q;
		}

		public void shutdown() {
			_isRunning = false;
		}

		@Override
		public void run() {
			while (_isRunning) {
				try {
					Work w = _q.take(); // blocking
					_iter++;
					_sum += w._payload;
					if (_verbose && _sum % 10 == 0)
						System.out.println("taken: " + _iter);
					// we don't need to sleep!
				} catch (Exception e) {
					// ignore - part of the test
				}
			}

			if (_verbose)
				System.out.println("--> Take is done");
		}
	}

	/**
	 * monitors work performed
	 * 
	 * @author gash
	 *
	 */
	public static final class Monitor extends Thread {
		public boolean _verbose = false;
		public Put _put;
		public Take _take;
		public int _maxWork;
		public boolean _isRunning = true;

		public Monitor(Put p, Take t, int maxWork) {
			_put = p;
			_take = t;
			_maxWork = maxWork;
		}

		public void shutdown() {
			_isRunning = false;
			_take.shutdown();
			_put.shutdown();
		}

		@Override
		public void run() {
			while (_isRunning) {
				if (_maxWork == 0) {
					shutdown();
				} else {
					_maxWork--;
					if (_verbose) {
						System.out.println("Monitor: " + _maxWork + ", P: " + _put._sum + ", T: " + _take._sum);
					}
					try {
						Thread.sleep(200);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	/**
	 * run our test
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		AsMonitor aa = new AsMonitor();
		aa.iterate(20);
	}
}
