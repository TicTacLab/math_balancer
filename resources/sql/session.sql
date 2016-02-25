-- name: get-expire*
SELECT expire
FROM sessions
WHERE session_id = :session_id

-- name: get-user-with-password*
SELECT password, status, is_admin, login
FROM users
WHERE login = :login;