package launcher.runtime.dialog;

import javafx.concurrent.Task;
import launcher.helper.CommonHelper;

public final class DialogTask<V> extends Task<V> {
	private final Runnable<V> runnable;

	public DialogTask(Runnable<V> runnable) {
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
