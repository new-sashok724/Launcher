package launcher.runtime.dialog.overlay;

import java.io.IOException;
import java.net.URL;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.Pane;
import launcher.request.Request;
import launcher.runtime.dialog.DialogController;
import launcher.runtime.dialog.DialogTask;

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
