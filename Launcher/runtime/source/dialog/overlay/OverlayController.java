package launcher.runtime.dialog.overlay;

import java.io.IOException;
import java.net.URL;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import launcher.helper.CommonHelper;
import launcher.request.Request;
import launcher.runtime.dialog.DialogController;

public abstract class OverlayController {
	@FXML private Parent overlay;

	public OverlayController(URL fxmlURL) throws IOException {
		DialogController.loadFXML(fxmlURL, this);
	}

	public final Parent getOverlay() {
		return overlay;
	}

	public abstract void reset();

	public static <V> OverlayTask<V> newRequestTask(Request<V> request) {
		return new OverlayTask<>(request::request);
	}

	protected static final class OverlayTask<V> extends Task<V> {
		private final Runnable<V> runnable;

		public OverlayTask(Runnable<V> runnable) {
			this.runnable = runnable;
		}

		@Override
		public void updateMessage(String message) {
			super.updateMessage(message);
		}

		@Override
		public void updateProgress(double workDone, double max) {
			super.updateProgress(workDone, max);
		}

		@Override
		public void updateProgress(long workDone, long max) {
			super.updateProgress(workDone, max);
		}

		@Override
		public void updateTitle(String title) {
			super.updateTitle(title);
		}

		@Override
		protected V call() throws Exception {
			return runnable.call();
		}

		public void start() {
			CommonHelper.newThread("Task Thread", true, this).start();
		}

		public interface Runnable<V> {
			V call() throws Exception;
		}
	}
}
