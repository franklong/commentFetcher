Example run parameters:
==============
-f  Z:\_\googleplay\comments.csv

-u androidwe7@gmail.com

-p we7monday

-e

-fu

-s

-eto barryi@blinkbox.com sven@blinkbox.com andrewa@blinkbox.com andrewba@blinkbox.com frankl@blinkbox.com simonl@blinkbox.com chrisn@blinkbox.com louise@blinkbox.com

-r 4


HELP:
==============
-e,--excel                  Use EXCEL format
 
 -f,--filename <arg>         File name to save comments to, will append
 
 -fa,--all                   Fetch all available comments, One of fx is
                             required
                             
 -fd,--date <arg>            Fetch by date (takes a timestamp, One of fx
                             is required)
                             
 -fn,--number <arg>          Fetch specified number comments, One of fx is
                             required
                             
 -fr,--range <arg>           Fetch range example "1 20" will return the
                             first 20 records, use -1 to go to the end.
                             One of fx is required
                             
 -fu,--update                Fetch latest, use this to update the file,
                             One of fx is required
                             
 -h,--help                   Display usage
 
 -i,--package <arg>          Package name of the app, default is
                             com.we7.player
                             
 -m,--recovery <arg>         Time to wait before another request when a
                             429 has been issued
                             
 -p,--password <arg>         Password of the google account
 
 -s,--sort                   Sort on multiple columns (takes a comma
                             delimited string of zero based numbers e.g.
                             2,1,3)
                             
 -t,--throttle <arg>         Throttle time between requests, necessary
 
 -u,--username <arg>         Username of the google account
 
 -eto,--emailTo <arg(s)>     Email to. Multiple email addresses should be
                             separated by a space
                             
 -r,--ratingBoundary <arg>   Send email alert comprising of all star
                             ratings at this number or below (default is
                             2)
