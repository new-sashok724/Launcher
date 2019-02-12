<?php
header("Content-Type: text/plain; charset=UTF-8");
 
// Verify login and password
$login = $_GET['login'];
$password = $_GET['password'];
if(empty($login) || empty($password)) {
    exit('Empty login or password');
}
 
// Initialize Joomla
define('_JEXEC', 1);
if (file_exists(__DIR__ . '/defines.php')) {
    include_once __DIR__ . '/defines.php';
}
if (!defined('_JDEFINES')) {
    define('JPATH_BASE', __DIR__);
    require_once JPATH_BASE . '/includes/defines.php';
}
require_once JPATH_BASE . '/includes/framework.php';
$app = JFactory::getApplication('site');
 
// Try to login
$credentials = array();
$credentials['username'] = $login;
$credentials['password'] = $password;
$options = array();
$options['remember'] = false;
$logged_in = $app->login($credentials, $options) === true;
 
// We're done
echo($logged_in ? 'OK:' . $login : 'Incorrect login or password');
?>
