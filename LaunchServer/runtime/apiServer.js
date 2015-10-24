server.loadScript(IOHelperClass.static.toURL(java.nio.file.Paths.get("api.js")));
var LaunchServer = LaunchServerClass.static;

// Auth class API imports
var AuthHandler = AuthHandlerClass.static;
var FileAuthHandler = FileAuthHandlerClass.static;
var CachedAuthHandler = CachedAuthHandlerClass.static;
var AuthProvider = AuthProviderClass.static;
var DigestAuthProvider = DigestAuthProviderClass.static;
var AuthException = AuthExceptionClass.static;

// Command class API imports
var Command = CommandClass.static;
var CommandHandler = CommandHandlerClass.static;
var CommandException = CommandExceptionClass.static;

// Response class API imports
var Response = ResponseClass.static;
var ResponseFactory = ResponseFactoryClass.static;
var ServerSocketHandlerListener = ServerSocketHandlerListenerClass.static;
