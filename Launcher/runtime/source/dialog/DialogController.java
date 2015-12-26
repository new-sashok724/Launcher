package launcher.runtime.dialog;

import java.io.IOException;
import java.net.URL;
import java.nio.file.NoSuchFileException;
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
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextField;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import launcher.client.ClientProfile;
import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.helper.VerifyHelper;
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
	@FXML private Hyperlink link;

	// Action controls
	@FXML private ButtonBase play, settings;

	public DialogController(Application app, Stage stage) {
		this.app = app;
		this.stage = stage;
	}

	public void initOfflineMode() throws NoSuchFileException {
		stage.setTitle(stage.getTitle() + " [Offline]");

		// Set login field as username field
		login.setPromptText(LOCALE.getString("dialog.username"));
		if (!VerifyHelper.isValidUsername(Mainclass.SETTINGS.getLogin())) {
			login.setText(""); // Reset if not valid
		}

		// Disable password field
		password.setDisable(true);
		password.setPromptText(LOCALE.getString("dialog.unavailable"));
		password.setText("");

		// Switch news view to offline page
		URL offlineURL = IOHelper.getResourceURL("launcher/runtime/dialog/offline/offline.html");
		news.getEngine().load(offlineURL.toString());
	}

	public Parent loadDialog() throws IOException {
		loadFXML(IOHelper.getResourceURL("launcher/runtime/dialog/dialog.fxml"), this);

		// Initialize webview
		news.setContextMenuEnabled(false);
		WebEngine engine = news.getEngine();
		engine.setUserDataDirectory(Mainclass.DIR.resolve("webserver").toFile());
		engine.load("https://launcher.sashok724.net/");

		// Initialize login field
		login.setOnAction(this::play);
		String loginValue = Mainclass.SETTINGS.getLogin();
		if (loginValue != null) {
			login.setText(loginValue);
		}

		// Initialize password field
		password.setOnAction(this::play);
		if (Mainclass.SETTINGS.getPassword() != null) {
			password.getStyleClass().add("hasSaved");
			password.setPromptText(LOCALE.getString("dialog.savedPassword"));
		}

		// Initialize save password checkbox
		savePassword.setSelected(Mainclass.SETTINGS.isPasswordSaved());

		// Initialize hyperlink
		link.setText(Mainclass.CONFIG.getProperty("dialog.link.text", "Missing text"));
		link.setOnAction(e -> app.getHostServices().showDocument(
			Mainclass.CONFIG.getProperty("dialog.link.url", "https://mysite.tld/")));

		// Initialize action buttons
		play.setOnAction(this::play);
		settings.setOnAction(this::settings);

		// Return initialized root
		return root;
	}

	private void play(ActionEvent e) {
		LogHelper.debug("Play");
	}

	private void settings(ActionEvent e) {
		LogHelper.debug("Settings");
	}

	public static Node loadFXML(URL url, Object controller) throws IOException {
		FXMLLoader loader = new FXMLLoader(url, LOCALE);
		loader.setCharset(IOHelper.UNICODE_CHARSET);
		loader.setController(controller);
		return loader.load();
	}
}
