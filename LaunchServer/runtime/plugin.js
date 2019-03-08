server.loadScript(IOHelperClass.static.toURL(java.nio.file.Paths.get("api.js")));

// Print test message
LogHelper.info("[plugin.js] Test message");

// Register command
server.commandHandler.registerCommand("test", new (Java.extend(Command, {
	getArgsDescription: function() { return "[anything]"; },
	getUsageDescription: function() { return "plugin.js test command"; },

	invoke: function(args) {
		LogHelper.info("[plugin.js] Command invoked! Args: " + java.util.Arrays.toString(args));
	}
}))(server));

// Register custom response
server.serverSocketHandler.registerCustomResponse("test", function(server, id, input, output) {
	return new (Java.extend(Response, function() {
		LogHelper.info("[plugin.js] Custom response invoked!");
		output.writeInt(0x724);
	}))(server, id, input, output);
});

/* You can test custom request like this:
 var TestCustomRequest = Java.extend(CustomRequest, {
 getName: function() { return "test"; },

 requestDoCustom: function(input, output) {
 return input.readInt();
 }
 });
 var answer = new TestCustomRequest().request();
 LogHelper.info(java.lang.Integer.toHexString(answer));
*/
