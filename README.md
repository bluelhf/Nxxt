# **BIG NEWS!**

Future releases of Nxxt (b4 and above) will be made in **Java!**

This means cross-platform compatibility and a slick GUI redesign!
Unfortunately, this also means that I have to recode Nxxt from scratch,
which will take time..

This is how it'll look!

![Cool new UI!](https://imgur.com/UyO5wbm.png)

# Nxxt
A repository for releases of Nxxt Autoclicker.
This is where all of the executables will be posted as releases.
Yes, source code will be uploaded here.

## Using Nxxt
Nxxt is a completely stand-alone executable. It shouldn't have any dependencies.

## Building Nxxt
If you want to get a probably-not-working-but-maybe-has-cool-things version of Nxxt, you can
build it directly from this repository!
1. [Install Maven.](https://maven.apache.org/install.html)
2. Download and extract this repository.
3. Open Terminal / Command Prompt in the extracted repository
4. Run `mvn verify`
5. Get your .jar from the `target` subdirectory
6. Profit!

### Windows Defender
Running Nxxt between versions B1 and B2 might greet you with a "Windows protected your PC" screen.

This is Windows Defender's SmartScreen. It recognises .exe files with no publisher
and gives you a fair warning. To run the executable anyways (in case you see this),
click on the "More Info" text and then click the "Run Anyway" button.
