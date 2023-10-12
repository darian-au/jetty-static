# jetty-static
### An Embedded Jetty HTTP Static File Server

A simple HTTP File Server that serves static content from the file system.

Specifically filters on the `User-Agent` header for `Let's Encrypt validation server`.

Easily start and stop the server from the command line, optionally specifying the port and directory to serve.

    java -jar jetty-static.jar (start | stop) [staticDir] [httpPort] [securePort] [keystoreType] [keystoreFile] [keystorePwd]

  `staticDir`:    the static directory to serve files from, default the current directory `.`  
  `httpPort`:     the web server http port, default port `8080`  
  `securePort`:   the web server secure https port, default disabled (port `0`)  
  `keystoreType`: the type of jks keystore file, eg `PKCS12`  
  `keystoreFile`: the path to the jks keystore file  
  `keystorePwd`:  the jks keystore password  

Example usage:

    java -jar jetty-static.jar start ~/www/ 8080 &

Importing a PEM file to a keystore file:

    openssl pkcs12 -export -inkey privatekey.pem -in fullchain.pem -out keystore.pkcs12 -passout pass:pkcs12password -legacy
    keytool -importkeystore -noprompt -srckeystore keystore.pkcs12 -srcstoretype pkcs12 -srcstorepass pkcs12password -destkeystore keystore.jks -deststorepass jkspassword -destkeypass jkspassword -deststoretype pkcs12
