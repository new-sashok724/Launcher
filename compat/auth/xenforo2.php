<?php
header("Content-Type: text/plain; charset=UTF-8");
 
// Verify login and password
$login = $_GET['login'];
$password = $_GET['password'];
if(empty($login) || empty($password)) {
    exit('Empty login or password');
}
 
// Load XenForo core and login service
$dir = dirname(__FILE__);
require_once($dir . '/src/XF.php');
XF::start($dir);
$app = \XF::setupApp('XF\Pub\App');
$loginService = $app->service('XF:User\Login', $login, $app->request->getIp());
 
// Try authenticate user
$error = null;
$user = $loginService->validate($password, $error);
echo($user ? 'OK:' . $user->username : 'Incorrect login or password');
?>
