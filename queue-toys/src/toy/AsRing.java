package toy;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Evolving AsSimulation to a 5 node
 * 
 * classes are:
 * 
 * <pre>
 * Client --> Comm --> Server x 5
 *                   (ring network w/ s1 --> Comm --> s2)    
 *  
 *  creating a unidirectional ring of servers with a client attached to one server
 *  
 *  For you to complete:
 *  0) Verify by sending messages?
 *  1) Implement a heartbeat
 *  2) Implement a separate queue for work verses other tasks
 *  3) Implement a voting strategy for a leader
 * </pre>
 * 
 * @author gash
 *
 */
public class AsRing {
	private static final int sMaxWork = 10;
	private static final int sRingSize = 5;

	private boolean _verbose = false;
	private int _iter = 1;

	// players
	private Put _put; // external client making request
	private ArrayList<Take> _servers;

	public static Clock _clock;

	static {
		// This interval should be less than the server check interval
		_clock = new Clock(500);
		_clock.start();
	}

	public AsRing(int size) {
		setup(size);
	}

	/**
	 * run the test 'n' times
	 * 
	 * @param times
	 */
	public void iterate(int times) {
		_iter = times;
		for (; _iter > 0; _iter--) {
			runOnce();
			report();
		}

		System.out.println("--> terminating test ");
		for (var s : _servers) {
			s.shutdown();
		}
		_clock.shutdown();
	}

	private void setup(int size) {
		if (size < 2)
			throw new RuntimeException("Network must be greater than 2 servers!");

		System.out.println("creating " + size + " servers");
		_servers = new ArrayList<Take>(size);
		for (int i = 0; i < size; i++) {
			_servers.add(new Take(i, 1)); // only one worker per server
		}

		// wire the network into a unidirectional ring
		var I = _servers.size() - 1;
		for (int i = 0; i < I; i++) {
			var s = _servers.get(i);
			var s2 = _servers.get(i + 1);
			var c = new Comm();
			s.addOutComm(c);
			s2.addInComm(c);

			System.out.println("linking " + s._id + " to " + s2._id);
		}

		// connect first and last
		var c = new Comm();
		var first = _servers.get(0);
		var last = _servers.get(I);
		last.addOutComm(c);
		first.addInComm(c);
		System.out.println("linking " + last._id + " to " + first._id);

		// start the network (ring)
		for (Take s : _servers) {
			s.start();
		}

		// lastly, the client needs to connect to a server
		var ctos = new Comm();
		_servers.get(0).addClient(ctos);
		_put = new Put(ctos, size);
	}

	public void runOnce() {
		if (_verbose)
			System.out.println("--> starting test " + _iter);

		// TODO use put to send work into the network
		_put.addWork(10);

		// fixed waiting period (wait x 500) for the test to complete
		int waiting = 100;
		while (waiting > 0) {
			try {
				waiting--;

				if (_verbose)
					System.out.println("--> waiting...");
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

	public void report() {
		System.out.flush();
		System.out.println("-----------------------------");
		for (var s : _servers) {
			System.out.println(s);
		}
		System.out.println("-----------------------------");
		System.out.flush();
	}

	/**
	 * the work
	 * 
	 * @author gash
	 *
	 */
	public static final class Work {
		public enum WorkType {
			Work, Response
		}

		private static AtomicInteger sIdGen;

		public final int workId;
		public int fromId;
		public int toId;
		public WorkType isA;

		// a placeholder
		public int payload;

		static {
			sIdGen = new AtomicInteger();
		}

		public Work(int fromId, int toId, WorkType isA, int payload) {
			this.workId = sIdGen.incrementAndGet();
			this.fromId = fromId;
			this.toId = toId;
			this.isA = isA;
			this.payload = payload;

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

				r = _queue.poll(2, TimeUnit.SECONDS);

			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			return r;
		}

	}

	/**
	 * Client - puts work on the queue
	 */
	public static final class Put {
		private int _id = -1;
		private Comm _comm; // the connection to a server
		private int _numServers;

		private boolean _verbose = true;

		public Put(Comm comm, int numServers) {
			_comm = comm;
			_numServers = numServers;
		}

		public void addWork(int amount) {

			for (int n = 0; n < amount; n++) {

				try {
					// setting which server will receive the work
					var toServer = (n % _numServers);

					if (_verbose)
						System.out.println("---> putting " + n + " w destination " + toServer);

					_comm.write(new Work(_id, toServer, Work.WorkType.Work, n));

					Thread.sleep(100);
				} catch (Exception e1) {
					e1.printStackTrace();
					break;
				}
			}

			if (_verbose)
				System.out.println("--> Put added " + amount + " work requests");
		}
	}

	/**
	 * Server - takes work from the client
	 */
	public static final class Clock extends Thread {

		private AtomicInteger _clock;
		private int _tick;
		private boolean _verbose = false;
		private boolean _isRunning = true;

		public Clock(int msec) {
			_tick = msec;
			_clock = new AtomicInteger();
		}

		public void shutdown() {
			_isRunning = false;
		}

		public int getTick() {
			return _clock.get();
		}

		@Override
		public void run() {
			while (_isRunning) {
				_clock.incrementAndGet();

				if (_verbose)
					System.out.println("clock " + _clock.get());

				try {
					Thread.sleep(_tick);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			System.out.println("Clock shutting down");
		}
	}

	/**
	 * Server - takes work from the client
	 */
	public static final class Take extends Thread {
		private int _id;
		private boolean _verbose = true;
		private int _sum = 0; // TODO we are cheating here!
		private boolean _isRunning = true;

		// Communication (external client, previous, next)
		private Comm _cExIn, _cIn, _cOut;

		private ArrayList<Worker> _workers = new ArrayList<Worker>();

		// server's internal queue
		private LinkedBlockingDeque<Work> _qWaitingToComplete;

		public Take(int myId, int numWorkers) {
			_id = myId;

			// internal queue to decouple request and response
			_qWaitingToComplete = new LinkedBlockingDeque<Work>();

			// create workers
			if (numWorkers < 1)
				numWorkers = 1;

			for (int i = 0; i < numWorkers; i++) {
				// giving worker direct access to the server's queue
				// TODO Is this a good idea?
				Worker w = new Worker(this, i, _qWaitingToComplete);
				_workers.add(w);
				w.start();
			}
		}

		public void shutdown() {
			_isRunning = false;
			if (_cIn != null)
				_cIn.shutdown();
			if (_cOut != null)
				_cOut.shutdown();

			// shutdown workers
			if (!_workers.isEmpty()) {
				for (var w : _workers) {
					w.shutdown();
				}
				_workers.clear();
			}
		}

		@Override
		public String toString() {
			var sb = new StringBuilder();
			sb.append("Server ").append(_id).append(", sum: ").append(_sum).append(", w: ");

			for (var w : _workers) {
				sb.append(w.workDone()).append(" ");
			}

			return sb.toString();
		}

		public void addOutComm(Comm comm) {
			_cOut = comm;
		}

		public void addInComm(Comm comm) {
			_cIn = comm;
		}

		public void addClient(Comm commFromClient) {
			_cExIn = commFromClient;
		}

		protected void send(Work w) throws Exception {
			_cOut.write(w);
		}

		@Override
		public void run() {
			while (_isRunning) {
				if (_verbose && _sum % 2 == 0)
					System.out.println("server " + _id + " checking " + AsRing._clock.getTick());

				try {
					// reading from any client attached to network
					if (_cExIn != null) {
						Work cw = _cExIn.read();
						if (cw != null)
							_qWaitingToComplete.add(cw);
					}

					// reading from the network of servers
					Work w = _cIn.read();
					if (w == null)
						continue;

					// decoupling the request from the work
					_qWaitingToComplete.add(w);

					if (_verbose && _sum % 10 == 0)
						System.out.println("server " + _id + " enqueue task: " + w.workId);
				} catch (Exception e) {
					// ignore - part of the test
				}
			}

			System.out.println("Server " + _id + " shutting down");
		}

		// cheating to allow the workers access to the server's counter (sum)
		public synchronized void increment(int value) {
			_sum += value;
		}
	}

	/**
	 * Worker - takes from the server's internal queue
	 */
	public static final class Worker extends Thread {
		private Take _server;
		private int _id;
		private int _jobsDone;
		private LinkedBlockingDeque<Work> _qTakeFrom;
		private boolean _running = true;
		private boolean _verbose = true;

		public Worker(Take belongsTo, int id, LinkedBlockingDeque<Work> qToTakeFrom) {
			_server = belongsTo;
			_id = id;
			_qTakeFrom = qToTakeFrom; // TODO what if null?
		}

		public void shutdown() {
			_running = false;
		}

		public int workDone() {
			return _jobsDone;
		}

		@Override
		public void run() {
			while (_running) {
				try {
					// subtle change here - we are still blocking but now on 2
					// sec intervals so we can check for shutdowns
					Work w = null;
					while (w == null && _running) {
						w = _qTakeFrom.poll(1, TimeUnit.SECONDS);

						if (w == null)
							continue;

						// TODO do work and reply

						if (w.toId == _server._id) {
							if (w.isA == Work.WorkType.Work) {
								if (_verbose) {
									System.out.println("server " + _id + ", thread " + _id + " doing work " + w.workId);
									System.out.flush();
								}

								_jobsDone++;
								_server.increment(w.payload);
								var reply = new Work(_server._id, w.fromId, Work.WorkType.Response, w.payload);
								_server.send(reply);
							} else if (w.isA == Work.WorkType.Response) {
								// TODO how do I handle this?
							}
						} else {
							// not for me, forward
							_server.send(w);
						}

					}
				} catch (Exception e) {
					e.printStackTrace();
					shutdown();
					break;
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
		AsRing aa = new AsRing(5);
		aa.iterate(1);
	}
}
