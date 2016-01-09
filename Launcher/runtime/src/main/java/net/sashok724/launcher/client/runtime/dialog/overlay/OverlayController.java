package net.sashok724.launcher.client.runtime.dialog.overlay;

import java.io.IOException;
import java.net.URL;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.Pane;
import net.sashok724.launcher.client.request.Request;
import net.sashok724.launcher.client.runtime.dialog.DialogController;
import net.sashok724.launcher.client.runtime.dialog.DialogTask;

public abstract class OverlayController implements Initializable {
	@FXML private Pane overlay;

	public OverlayController(URL fxmlURL) throws IOException {
		DialogController.loadFXML(fxmlURL, this);
	}

	public final Pane getOverlay() {
		return overlay;
	}

	public abstract void reset();

	public static <V> DialogTask<V> newRequestTask(Request<V> request) {
		return new DialogTask<>(request::request);
	}
}
