function tryWithResources(closeable, f) {
    try {
        f(closeable);
    } finally {
        IOHelper.close(closeable);
    }
}

function newTask(r) {
    return new javafx.concurrent.Task() { call: r };
}

function newRequestTask(request) {
    return newTask(function() request.request());
}

function startTask(task) {
    CommonHelper.newThread("FX Task Thread", true, task).start();
}

function openURL(url) {
    app.getHostServices().showDocument(url.toURI());
}
