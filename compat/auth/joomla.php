<?php
header("Content-Type: text/plain; charset=UTF-8");

function init(){
    define( '_JEXEC', 1 ) or die;

    define( 'DS', DIRECTORY_SEPARATOR );
    define('JPATH_BASE', dirname(__FILE__) );

    require_once ( JPATH_BASE.DS.'includes'.DS.'defines.php' );
    require_once ( JPATH_BASE.DS.'includes'.DS.'framework.php' );

    $mainframe = & JFactory::getApplication('site');
    $mainframe->initialise();
}

function checkCredentials($username, $password){
    $db = JFactory::getDbo();
    $query  = $db->getQuery(true);
    $query->select('id, password');
    $query->from('#__users');
    $query->where('username=' . $db->Quote($username));
    $db->setQuery($query);
    $result = $db->loadObject();

    if ($result){
        $user = JFactory::getUser($result->id);
        $match = JUserHelper::verifyPassword($password, $user->password, $user->id);
        return $match;
    } else {
        return false;
    }
}

$login = $_GET['login'];
$password = $_GET['password'];
if(empty($login) || empty($password)) {
    exit('Empty login or password');
}

init();
echo checkCredentials($login, $password) ? "OK:" . $login : "Incorrect login or password";