package launcher.runtime.dialog;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.TextField;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import launcher.client.ClientProfile;
import launcher.helper.IOHelper;
import launcher.runtime.Mainclass;
import launcher.serialize.signed.SignedObjectHolder;

public final class DialogController {
	private static final ResourceBundle LOCALE = ResourceBundle.getBundle("launcher.runtime.dialog.locale");

	// Instance
	private final Application app;
	private final Stage stage;

	// Layout
	@FXML private Parent root, layout, overlayDim;
	@FXML private WebView news;

	// Input controls
	@FXML private CheckBox savePassword;
	@FXML private TextField login, password;
	@FXML private ComboBoxBase<SignedObjectHolder<ClientProfile>> profiles;

	// Action controls
	@FXML private ButtonBase play, settings;

	public DialogController(Application app, Stage stage) {
		this.app = app;
		this.stage = stage;
	}

	public Parent loadDialog() throws IOException {
		loadFXML(IOHelper.getResourceURL("launcher/runtime/dialog/dialog.fxml"), this);

		// Initialize webview
		news.setContextMenuEnabled(false);
		WebEngine engine = news.getEngine();
		engine.setUserDataDirectory(Mainclass.DIR.resolve("webserver").toFile());
		engine.load("https://launcher.sashok724.net/");

		// Initialize login field TODO
		login.setOnAction(this::play);

		// Return initialized root
		return root;
	}

	private void play(ActionEvent actionEvent) {

	}

	public static Node loadFXML(URL url, Object controller) throws IOException {
		FXMLLoader loader = new FXMLLoader(url, LOCALE);
		loader.setCharset(IOHelper.UNICODE_CHARSET);
		loader.setController(controller);
		return loader.load();
	}
}
