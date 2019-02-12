<?php
header("Content-Type: text/plain; charset=UTF-8");
 
// Verify login and password
$login = $_GET['login'];
$password = $_GET['password'];
if(empty($login) || empty($password)) {
    exit('Empty login or password');
}
 
// Initialize DLE bindings
@ob_start();
@ob_implicit_flush(0);
@error_reporting(E_ALL ^ E_WARNING ^ E_DEPRECATED ^ E_NOTICE);
@ini_set('error_reporting', E_ALL ^ E_WARNING ^ E_DEPRECATED ^ E_NOTICE);
@ini_set('display_errors', true);
@ini_set('html_errors', false);
 
define('DATALIFEENGINE', true);
define('ROOT_DIR', dirname(__FILE__));
define('ENGINE_DIR', ROOT_DIR . '/engine');
 
require_once(ENGINE_DIR . '/inc/include/init.php');
$login = $db->safesql((string) $login);
 
// Verify password
$is_logged = false;
$member_id = $db->super_query("SELECT * FROM " . USERPREFIX . "_users WHERE name='{$login}' OR email='{$login}' LIMIT 1");
if($member_id['user_id'] AND $member_id['password']) {
    if(is_md5hash($member_id['password'])) {
        if($member_id['password'] == md5(md5($password))) {
            $is_logged = true;
        }
    } else if(password_verify($password, $member_id['password'] ) ) {
        $is_logged = true;
    }
}
 
// We're done
echo($is_logged ? 'OK:' . $member_id['name'] : 'Incorrect login or password');
?>
