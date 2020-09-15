import subprocess
import time


def exec_command(speed1, speed2):
    return "/home/tom/ownCloud/uni/java/jdk-13.0.1/bin/java -javaagent:/home/tom/ownCloud/uni/java/idea-IC-192.7142.36/lib/idea_rt.jar=46731:/home/tom/ownCloud/uni/java/idea-IC-192.7142.36/bin -Dfile.encoding=UTF-8 -classpath /home/tom/uniproj/conprog/trainspotting/out/production/trainspotting Main Lab1.map {} {}".format(speed1, speed2)


speeds = [
    (1, 20),
    (5, 20),
    (10, 20),
    (20, 20),
    (20, 10),
    (20, 5),
    (20, 1),
    (10, 10),
    (15, 20),
    (20, 15),
]
for speed1, speed2 in speeds:
    print()
    print("Running with speeds", speed1, "and", speed2)
    print()
    subprocess.run(exec_command(speed1, speed2), shell=True)
