net stop mysql80
Start-Process "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe" --skip-grant-tables --skip-networking -NoNewWindow
Start-Sleep 5
mysql -u root -e "FLUSH PRIVILEGES; ALTER USER 'root'@'localhost' IDENTIFIED BY ''; FLUSH PRIVILEGES;"
taskkill /IM mysqld.exe /F
net start mysql80