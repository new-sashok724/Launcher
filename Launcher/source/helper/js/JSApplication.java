package launcher.helper.js;

import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Application;

import launcher.LauncherAPI;

@LauncherAPI
@SuppressWarnings("AbstractClassNeverImplemented")
public abstract class JSApplication extends Application {
    private static final AtomicReference<JSApplication> INSTANCE = new AtomicReference<>();

    @SuppressWarnings("ConstructorNotProtectedInAbstractClass")
    public JSApplication() {
        INSTANCE.set(this);
    }

    public static JSApplication getInstance() {
        return INSTANCE.get();
    }
}
