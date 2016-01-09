package net.sashok724.launcher.client.runtime.dialog.overlay;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.security.SignatureException;
import java.util.ResourceBundle;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Labeled;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Callback;
import net.sashok724.launcher.client.Launcher;
import net.sashok724.launcher.client.client.ClientLauncher;
import net.sashok724.launcher.client.client.ClientProfile;
import net.sashok724.launcher.client.client.PlayerProfile;
import net.sashok724.launcher.client.hasher.HashedDir;
import net.sashok724.launcher.client.helper.IOHelper;
import net.sashok724.launcher.client.helper.LogHelper;
import net.sashok724.launcher.client.helper.SecurityHelper;
import net.sashok724.launcher.client.helper.VerifyHelper;
import net.sashok724.launcher.client.request.RequestException;
import net.sashok724.launcher.client.request.auth.AuthRequest;
import net.sashok724.launcher.client.request.update.LauncherUpdateRequest;
import net.sashok724.launcher.client.runtime.Mainclass;
import net.sashok724.launcher.client.runtime.dialog.DialogController;
import net.sashok724.launcher.client.runtime.dialog.DialogTask;
import net.sashok724.launcher.client.serialize.signed.SignedObjectHolder;

public final class SpinnerController extends OverlayController {
	private static final Image SPINNER_IMAGE, ERROR_IMAGE;

	// Instance
	private final DialogController dialog;
	private boolean offlineMode;

	// Controls
	@FXML private ImageView spinner;
	@FXML private Labeled description;

	public SpinnerController(DialogController dialog) throws IOException {
		super(IOHelper.getResourceURL("net.sashok724.launcher.client.runtime/dialog/overlay/spinner/spinner.fxml"));
		this.dialog = dialog;
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		reset();
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
		task.updateMessage("dialog.spinner.launchClient");
		task.start();
	}

	public void makeAuthRequest(String login, byte[] password, Callback<AuthRequest.Result, Void> callback) {
		DialogTask<AuthRequest.Result> task = offlineMode ?
			new DialogTask<>(() -> SpinnerController.offlineAuthRequest(login)) :
			newRequestTask(new AuthRequest(login, password));
		setTaskProperties(task, callback, null);

		// Start task
		task.updateMessage("dialog.spinner.auth");
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
		task.updateMessage("dialog.spinner.updateLauncher");
		task.start();
	}

	public void setError(String error) {
		spinner.setImage(ERROR_IMAGE);
		description.getStyleClass().add("error");
		setLocalizedDescription(error);
	}

	public <V> void setTaskProperties(Task<V> task, Callback<V, Void> callback, Callback<Throwable, Void> eCallback) {
		task.messageProperty().addListener((o, ov, nv) -> setLocalizedDescription(nv));
		task.setOnFailed(e -> {
			Throwable exc = task.getException();
			setError(exc.toString());
			if (eCallback != null) {
				eCallback.call(exc);
			}

			// Log error
			LogHelper.error(exc);
		});
		task.setOnSucceeded(e -> {
			if (callback != null) {
				callback.call(task.getValue());
			}
		});
	}

	private void setLocalizedDescription(String description) {
		if (DialogController.LOCALE.containsKey(description)) {
			this.description.setText(DialogController.LOCALE.getString(description));
		} else {
			this.description.setText(description);
		}
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
					"net.sashok724.launcher.client.runtime/dialog/overlay/spinner/images/spinner.gif").toString());
			ERROR_IMAGE = new Image(IOHelper.getResourceURL(
					"net.sashok724.launcher.client.runtime/dialog/overlay/spinner/images/error.png").toString());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
