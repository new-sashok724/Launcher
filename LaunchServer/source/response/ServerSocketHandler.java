package launchserver.response;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import launcher.LauncherAPI;
import launcher.helper.CommonHelper;
import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.helper.VerifyHelper;
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
	private volatile Predicate<Socket> connectListener;
	private volatile Consumer<Socket> disconnectListener;

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
				throw new IOException("Previous socket wasn'sizet closed");
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
				if (connectListener != null && !connectListener.test(socket)) {
					IOHelper.close(socket);
					continue;
				}

				// Filter passed
				threadPool.execute(new ResponseThread(server, socket));
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
		Response.Factory factory = customResponses.get(name);
		if (factory == null) {
			throw new IOException(String.format("Unknown custom response: '%s'", name));
		}
		return factory.newResponse(server, input, output);
	}

	@LauncherAPI
	public void registerCustomResponse(String name, Response.Factory factory) {
		VerifyHelper.verifyIDName(name);
		VerifyHelper.verify(customResponses.putIfAbsent(name, Objects.requireNonNull(factory, "factory")),
			c -> c == null, String.format("Custom response has been already registered: '%s'", name));
	}

	@LauncherAPI
	public void setConnectListener(Predicate<Socket> connectListener) {
		this.connectListener = connectListener;
	}

	@LauncherAPI
	public void setDisconnectListener(Consumer<Socket> disconnectListener) {
		this.disconnectListener = disconnectListener;
	}

	/*package*/ void onDisconnected(Socket socket) {
		if (disconnectListener != null) {
			disconnectListener.accept(socket);
		}
	}
}
