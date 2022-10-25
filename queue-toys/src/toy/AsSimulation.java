package toy;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * Evolving AsMonitor to a simulation of a client, server, and channel (socket)
 * whilst maintaining MT safety.
 * 
 * classes are:
 * 
 * <pre>
 * Client --> Comm --> Server -- ? --> AnotherServer(?)
 *  ^       (socket)      ^
 *  |                     |  
 *   ---------------------
 *           |
 *        Monitor (observer)
 *        
 *  Server:
 *  Impl.request() --> Queue <-- Workers (threads, take)
 *  
 *  Client:
 *  request --> Server
 *  
 *  For you to complete:
 *  0) Does this code work?
 *  1) Server's Worker --> reply
 *  2) Creating: Client --> Server --> AnotherServer
 *  3) How would you quantify a server's or worker's effort spent (accumulated work)? Why?
 * </pre>
 * 
 * @author gash
 *
 */
public class AsSimulation {
	private static final int sMaxWork = 10;

	private boolean _verbose = false;
	private int _iter = 1;

	// players
	private Comm _comm; // network **NEW**
	private Put _put; // client
	private Take _take; // server
	private Monitor _monitor;

	/**
	 * run the test 'n' times
	 * 
	 * @param times
	 */
	public void iterate(int times) {
		_iter = times;
		for (; _iter > 0; _iter--) {
			setup();
			runOnce();
			report();
		}
	}

	public void setup() {
		_comm = new Comm();
		_put = new Put(_comm);
		_take = new Take(_comm, 2);
		_monitor = new Monitor(_put, _take, AsSimulation.sMaxWork);
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
					System.out.println("--> waiting...");
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
	 * Comm - represents the communication (socket/channel)
	 */
	public static final class Comm {
		// this is our network simulation
		private LinkedBlockingDeque<Work> _queue;
		private boolean _running = true;

		public Comm() {
			_queue = new LinkedBlockingDeque<Work>();
		}

		public void shutdown() {
			_queue.clear();
			_running = false;
		}

		public void write(Work w) throws Exception {
			if (!_running)
				throw new RuntimeException("queue is shutdown");

			if (w != null)
				_queue.add(w);
		}

		public Work read() {
			if (!_running)
				throw new RuntimeException("queue is shutdown");

			Work r = null;
			try {
				// subtle change here - we are still blocking but on 2 sec
				// intervals so we can check for shutdowns
				while (r == null && _running) {
					r = _queue.poll(2, TimeUnit.SECONDS);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			return r;
		}

	}

	/**
	 * Client - puts work on the queue
	 */
	public static final class Put extends Thread {

		private Comm _comm; // this is our network
		private boolean _verbose = false;
		private int _genID = 0;
		private int _sum = 0;
		private boolean _isRunning = true;

		public Put(Comm comm) {
			_comm = comm;
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

				try {
					// writing (sending) to the network
					_comm.write(new Work(_genID, _genID));
				} catch (Exception e1) {
					e1.printStackTrace();
					break;
				}

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
	 * Server - takes work from the client
	 */
	public static final class Take extends Thread {
		private boolean _verbose = false;
		private static int _sum = 0; // TODO we are cheating here!
		private boolean _isRunning = true;

		private Comm _comm;

		private ArrayList<Worker> _workers;

		// server's internal queue
		private LinkedBlockingDeque<Work> _qWaitingToComplete;

		public Take(Comm comm, int numWorkers) {
			_comm = comm; // network

			// internal queue to decouple request and response
			_qWaitingToComplete = new LinkedBlockingDeque<Work>();

			// create workers
			if (numWorkers < 1)
				numWorkers = 1;
			for (int i = 0; i < numWorkers; i++) {
				// giving worker direct access to the server's queue
				// TODO Is this a good idea?
				Worker w = new Worker(i, _qWaitingToComplete);
				_workers.add(w);
				w.start();
			}
		}

		public void shutdown() {
			_isRunning = false;
			_comm.shutdown();

			// shutdown workers
			if (!_workers.isEmpty()) {
				for (var w : _workers) {
					w.shutdown();
				}
				_workers.clear();
			}
		}

		@Override
		public void run() {
			while (_isRunning) {
				try {
					// reading from the network - remember this is blocking!
					Work w = _comm.read();

					// decoupling the request from the work
					_qWaitingToComplete.add(w);

					if (_verbose && _sum % 10 == 0)
						System.out.println("server enqueue task: " + w._id);
				} catch (Exception e) {
					// ignore - part of the test
				}
			}

			if (_verbose)
				System.out.println("--> Take is done");
		}

		// cheating to allow the workers access to the server's counter (sum)
		public static synchronized void increment(int value) {
			_sum += value;
		}
	}

	/**
	 * Worker - takes from the server's internal queue
	 */
	public static final class Worker extends Thread {
		private int _id;
		private LinkedBlockingDeque<Work> _qTakeFrom;
		private boolean _running = true;
		private boolean _verbose = false;

		public Worker(int id, LinkedBlockingDeque<Work> qToTakeFrom) {
			_id = id;
			_qTakeFrom = qToTakeFrom; // TODO what if null?
		}

		public void shutdown() {
			_running = false;
		}

		@Override
		public void run() {
			while (_running) {
				try {
					// subtle change here - we are still blocking but now on 2
					// sec intervals so we can check for shutdowns
					Work w = null;
					while (w == null && _running) {
						w = _qTakeFrom.poll(2, TimeUnit.SECONDS);

						if (w != null)
							Take.increment(w._payload);
					}

					if (_verbose)
						System.out.println("worker " + _id + " performing: " + w._id);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * monitors work performed
	 * 
	 * @author gash
	 *
	 */
	public static final class Monitor extends Thread {
		private boolean _verbose = false;
		private Put _put;
		private Take _take;
		private int _maxWork;
		private boolean _isRunning = true;

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
		AsSimulation aa = new AsSimulation();
		aa.iterate(20);
	}
}
