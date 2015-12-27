package launcher.runtime.dialog.overlay;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SignatureException;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Labeled;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Callback;
import launcher.Launcher;
import launcher.client.ClientLauncher;
import launcher.client.ClientProfile;
import launcher.client.PlayerProfile;
import launcher.hasher.HashedDir;
import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.helper.VerifyHelper;
import launcher.request.RequestException;
import launcher.request.auth.AuthRequest;
import launcher.request.update.LauncherUpdateRequest;
import launcher.runtime.Mainclass;
import launcher.runtime.dialog.DialogController;
import launcher.runtime.dialog.DialogTask;
import launcher.serialize.signed.SignedObjectHolder;

public final class SpinnerController extends OverlayController {
	private static final Image SPINNER_IMAGE, ERROR_IMAGE;

	// Instance
	private final DialogController dialog;
	private boolean offlineMode;

	// Controls
	@FXML private ImageView spinner;
	@FXML private Labeled description;

	public SpinnerController(DialogController dialog) throws IOException {
		super(IOHelper.getResourceURL("launcher/runtime/dialog/overlay/spinner/spinner.fxml"));
		this.dialog = dialog;
	}

	@Override
	public void reset() {
		spinner.setImage(SPINNER_IMAGE);
		description.getStyleClass().remove("error");
		description.setText("");
	}

	public boolean isOfflineMode() {
		return offlineMode;
	}

	public void launchClient(Path jvmDir, SignedObjectHolder<HashedDir> jvmHDir,
		SignedObjectHolder<HashedDir> assetHDir, SignedObjectHolder<HashedDir> clientHDir,
		SignedObjectHolder<ClientProfile> profile, ClientLauncher.Params params, boolean pipeOutput,
		Callback<Process, Void> callback) {
		DialogTask<Process> task = new DialogTask<>(() ->
			ClientLauncher.launch(jvmDir, jvmHDir, assetHDir, clientHDir, profile, params, pipeOutput));
		setTaskProperties(task, callback, null);

		// Start task
		task.updateMessage("Запуск выбранного клиента");
		task.start();
	}

	public void makeAuthRequest(String login, byte[] password, Callback<AuthRequest.Result, Void> callback) {
		DialogTask<AuthRequest.Result> task = offlineMode ?
			new DialogTask<>(() -> SpinnerController.offlineAuthRequest(login)) :
			newRequestTask(new AuthRequest(login, password));
		setTaskProperties(task, callback, null);

		// Start task
		task.updateMessage("Авторизация на сервере");
		task.start();
	}

	public void makeLauncherUpdateRequest(Callback<LauncherUpdateRequest.Result, Void> callback) {
		DialogTask<LauncherUpdateRequest.Result> task = offlineMode ?
			new DialogTask<>(SpinnerController::offlineLauncherUpdateRequest) :
			newRequestTask(new LauncherUpdateRequest());
		setTaskProperties(task, callback, exc -> {
			if (offlineMode) { // We're already in offline
				return null;
			}

			// Repeat request, but in offline mode
			offlineMode = true;
			dialog.swapOverlay(2500.0D, getOverlay(), e -> makeLauncherUpdateRequest(callback));
			return null;
		});

		// Start task
		task.updateMessage("Обновление списка серверов");
		task.start();
	}

	public void setError(String error) {
		spinner.setImage(ERROR_IMAGE);
		description.getStyleClass().add("error");
		description.setText(error);
	}

	public <V> void setTaskProperties(Task<V> task, Callback<V, Void> callback, Callback<Throwable, Void> eCallback) {
		description.textProperty().bind(task.messageProperty());
		task.setOnFailed(e -> {
			description.textProperty().unbind();

			// Set error message
			Throwable exc = task.getException();
			setError(exc.toString());
			if (eCallback != null) {
				eCallback.call(exc);
			}

			// Log error
			LogHelper.error(exc);
		});
		task.setOnSucceeded(e -> {
			description.textProperty().unbind();
			if (callback != null) {
				callback.call(task.getValue());
			}
		});
	}

	private static AuthRequest.Result offlineAuthRequest(String username) throws RequestException {
		if (!VerifyHelper.isValidUsername(username)) {
			throw new RequestException("error.offline.invalidUsername");
		}
		return new AuthRequest.Result(PlayerProfile.newOfflineProfile(username), SecurityHelper.randomStringToken());
	}

	private static LauncherUpdateRequest.Result offlineLauncherUpdateRequest() throws IOException, SignatureException {
		LauncherUpdateRequest.Result result = Mainclass.SETTINGS.getLauncherUpdateRequest();
		if (result == null || !SecurityHelper.isValidSign(LauncherUpdateRequest.BINARY_PATH,
			result.getSign(), Launcher.Config.getDefault().publicKey)) {
			throw new RequestException("error.offline.invalidCache");
		}
		return result;
	}

	static {
		try {
			SPINNER_IMAGE = new Image(IOHelper.getResourceURL(
				"launcher/runtime/dialog/overlay/spinner/images/spinner.gif").toString());
			ERROR_IMAGE = new Image(IOHelper.getResourceURL(
				"launcher/runtime/dialog/overlay/spinner/images/error.png").toString());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
