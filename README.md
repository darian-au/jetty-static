# jetty-static
### An Embedded Jetty HTTP Static File Server

A simple HTTP File Server that serves static content from the file system.

Specifically filters on the `User-Agent` header for `Let's Encrypt validation server`.

Easily start and stop the server from the command line, optionally specifying the port and directory to serve.

    java -jar jetty-static.jar (start | stop) [httpPort] [staticDir]

  `httpPort`:   the web server port, default port `8080`
  `staticDir`:  the static directory to serve files from, default the current directory `.`

Example usage:

    java -jar jetty-static.jar start 80 ~/www/ &

    java -jar jetty-static.jar stop
