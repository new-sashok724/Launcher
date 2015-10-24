package launchserver.response;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import launcher.LauncherAPI;
import launcher.helper.CommonHelper;
import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.helper.VerifyHelper;
import launcher.request.Request;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launchserver.LaunchServer;

public final class ServerSocketHandler implements Runnable, AutoCloseable {
	private static final ThreadFactory THREAD_FACTORY = r -> CommonHelper.newThread("Network Thread", true, r);

	// Instance
	private final LaunchServer server;
	private final AtomicReference<ServerSocket> serverSocket = new AtomicReference<>();
	private final ExecutorService threadPool = Executors.newCachedThreadPool(THREAD_FACTORY);

	// API
	private final Map<String, Response.Factory> customResponses = new ConcurrentHashMap<>(2);
	private final AtomicInteger idCounter = new AtomicInteger(0);
	private volatile Listener listener;

	public ServerSocketHandler(LaunchServer server) {
		this.server = server;
	}

	@Override
	public void close() {
		ServerSocket socket = serverSocket.getAndSet(null);
		if (socket != null) {
			LogHelper.info("Closing server socket listener");
			try {
				socket.close();
			} catch (IOException e) {
				LogHelper.error(e);
			}
		}
	}

	@Override
	public void run() {
		LogHelper.info("Starting server socket thread");
		try (ServerSocket serverSocket = new ServerSocket()) {
			if (!this.serverSocket.compareAndSet(null, serverSocket)) {
				throw new IllegalStateException("Previous socket wasn't closed");
			}

			// Set socket params
			serverSocket.setReuseAddress(true);
			serverSocket.setPerformancePreferences(2, 1, 0);
			serverSocket.setReceiveBufferSize(IOHelper.BUFFER_SIZE);
			serverSocket.bind(server.getConfig().getSocketAddress());
			LogHelper.info("Server socket thread successfully started");

			// Listen for incoming connections
			while (serverSocket.isBound()) {
				Socket socket = serverSocket.accept();

				// Invoke pre-connect listener
				int id = idCounter.incrementAndGet();
				if (listener != null && !listener.onConnect(id, socket.getInetAddress())) {
					continue; // Listener didn't accepted this connection
				}

				// Reply in separate thread
				threadPool.execute(new ResponseThread(server, id, socket));
			}
		} catch (IOException e) {
			// Ignore error after close/rebind
			if (serverSocket.get() != null) {
				LogHelper.error(e);
			}
		}
	}

	@LauncherAPI
	public Response newCustomResponse(String name, HInput input, HOutput output) throws IOException {
		Response.Factory factory = VerifyHelper.getMapValue(customResponses, name,
			String.format("Unknown custom response: '%s'", name));
		return factory.newResponse(server, input, output);
	}

	@LauncherAPI
	public void registerCustomResponse(String name, Response.Factory factory) {
		VerifyHelper.verifyIDName(name);
		VerifyHelper.putIfAbsent(customResponses, name, Objects.requireNonNull(factory, "factory"),
			String.format("Custom response has been already registered: '%s'", name));
	}

	@LauncherAPI
	public void setListener(Listener listener) {
		this.listener = listener;
	}

	/*package*/ void onDisconnect(int id, Exception e) {
		if (listener != null) {
			listener.onDisconnect(id, e);
		}
	}

	/*package*/ boolean onHandshake(int id, Request.Type type) {
		return listener == null || listener.onHandshake(id, type);
	}

	public interface Listener {
		@LauncherAPI
		boolean onConnect(int id, InetAddress address);

		@LauncherAPI
		void onDisconnect(int id, Exception e);

		@LauncherAPI
		boolean onHandshake(int id, Request.Type type);
	}
}
