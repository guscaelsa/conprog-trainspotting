import TSim.*;
import javafx.util.Pair;

import java.util.Objects;
import java.util.concurrent.Semaphore;

public class Lab1 {
  public static Map map = new Map();

  public Lab1(int speed1, int speed2) {
    TSimInterface tsi = TSimInterface.getInstance();
    tsi.setDebug(false);

    new Thread(new TrainDriver(tsi, 1, speed1, map.brainA, map.brainB)).start();
    new Thread(new TrainDriver(tsi, 2, speed2, map.brainB, map.brainA)).start();
  }
}


class TrainDriver implements Runnable {
  protected int trainId;
  TSimInterface tsi;
  int speed;
  protected boolean reversing;

  public void waitFor(Semaphore lock) throws CommandException {
    if (!lock.tryAcquire()) {
      stop();
      lock.acquireUninterruptibly();
      if (reversing) {
        reverse();
      } else {
        advance();
      }
    }
  }

  interface Brain {
    boolean on_exit_sensor(SensorPos pos, TrainDriver drv) throws CommandException;
    boolean on_enter_sensor(SensorPos pos, TrainDriver drv) throws CommandException, InterruptedException;

    String adjective();
  }

  protected Brain brain;
  protected Brain _brainA;
  protected Brain _brainB;

  public TrainDriver(TSimInterface tsi, int trainId, int speed, Brain a, Brain b) {
    this.tsi = tsi;
    this.trainId = trainId;
    this.speed = speed;

    _brainA = a;
    _brainB = b;
    brain = a;
  }

  void advance() throws CommandException {
    if (trainId == 1) {
      tsi.setSpeed(trainId, speed);
      reversing = false;
    } else {
      tsi.setSpeed(trainId, -1 * speed);
      reversing = true;
    }
  }

  void stop() throws CommandException {
    tsi.setSpeed(trainId, 0);
  }

  void reverse() throws CommandException {
    if (trainId == 1) {
      tsi.setSpeed(trainId, -1 * speed);
      reversing = true;
    } else {
      tsi.setSpeed(trainId, speed);
      reversing = false;
    }
  }

  @Override
  public void run() {
    try {
      tsi.setSpeed(trainId, speed);

      while (true) {
        SensorEvent event = tsi.getSensor(trainId);
        if (event.getTrainId() != trainId) {
          throw new AssertionError("Thread got event for wrong train!");
        }

        boolean turn_around;
        if (event.getStatus() == 1) {
          turn_around = brain.on_enter_sensor(new SensorPos(event), this);
        } else {
          turn_around = brain.on_exit_sensor(new SensorPos(event), this);
        }

        if (turn_around) {
          if (brain == _brainA) {
            brain = _brainB;
          } else {
            brain = _brainA;
          }
          System.out.print("Train #");
          System.out.print(trainId);
          System.out.println(" is now " + brain.adjective());
        }
      }
    }
    catch (CommandException | InterruptedException e) {
      e.printStackTrace();    // or only e.getMessage() for the error
      System.exit(1);
    }

  }
}

class Map {
  public static String filename = "Lab1.map";

  static final SensorPos sr_topS = new SensorPos(14, 3);
  static final SensorPos sr_botS = new SensorPos(14, 5);
  static final SensorPos sr_botSS = new SensorPos(10, 5);
  static final SensorPos sr_topSS = new SensorPos(6, 5);
  static final SensorPos sr_topTT = new SensorPos(5, 11);
  static final SensorPos sr_botTT = new SensorPos(5, 13);
  static final SensorPos sr_topT = new SensorPos(14, 11);
  static final SensorPos sr_botT = new SensorPos(14, 13);

  static final SensorPos sr_enterS = new SensorPos(19,8);
  static final SensorPos sr_enterT = new SensorPos(1,11);

  static final SensorPos[] sensors = {
          new SensorPos(14, 3), // 0
          new SensorPos(14, 5),
          new SensorPos(6, 5),
          new SensorPos(10, 5),
          new SensorPos(11, 7),
          new SensorPos(10, 8), // 5
          new SensorPos(14, 7),
          new SensorPos(15, 8),
          new SensorPos(19, 8),
          new SensorPos(18, 9),
          new SensorPos(12, 9), // 10
          new SensorPos(13, 10),
          new SensorPos(7, 9),
          new SensorPos(6, 10),
          new SensorPos(1, 9),
          new SensorPos(1, 10), // 15
          new SensorPos(6, 11),
          new SensorPos(5, 13),
          new SensorPos(14, 11),
          new SensorPos(14, 13),
  };

  static final SwitchPos[] switches = {
          new SwitchPos(17, 7),
          new SwitchPos(15, 9),
          new SwitchPos(4, 9),
          new SwitchPos(3, 11),
  };

  static final Semaphore[] locks = {
          new Semaphore(0), // a/c
          new Semaphore(1), // b/d
          new Semaphore(1), // crossroad
          new Semaphore(1), // e
          new Semaphore(1), // f
          new Semaphore(1), // g
          new Semaphore(1), // h
          new Semaphore(0), // i
          new Semaphore(1), // j
  };

  static final Semaphore sem_botS = new Semaphore(1);
  static final Semaphore sem_middle = new Semaphore(1);
  static final Semaphore sem_topT = new Semaphore(0);

  SouthBoundBrain brainA = new SouthBoundBrain();
  NorthBoundBrain brainB = new NorthBoundBrain();

  static class SouthBoundBrain implements TrainDriver.Brain {
    @Override
    public String adjective() {
      return "south-bound";
    }

    @Override
    public boolean on_enter_sensor(SensorPos pos, TrainDriver drv) throws CommandException, InterruptedException {
      if (pos.equals(sensors[2]) || pos.equals(sensors[3])) {
        drv.waitFor(locks[2]);
        return false;
      }

      if (pos.equals(sensors[4]) || pos.equals(sensors[5])) {
        locks[2].release();
        return false;
      }

      if (pos.equals(sensors[6]) || pos.equals(sensors[7])) {
        drv.waitFor(locks[3]);
        if (pos.equals(sensors[6])) {
          switches[0].turn_right(drv.tsi);
        } else {
          switches[0].turn_left(drv.tsi);
        }
        return false;
      }

      if (pos.equals(sensors[9])) {
        if (locks[4].tryAcquire()) {
          switches[1].turn_right(drv.tsi);
        } else {
          if (!locks[5].tryAcquire()) {
            throw new AssertionError("Both L4 and L5 locked! I'm starving!");
          }
          switches[1].turn_left(drv.tsi);
        }
        return false;
      }

      if (pos.equals(sensors[10]) || pos.equals(sensors[11])) {
        locks[3].release();
        return false;
      }

      if (pos.equals(sensors[12]) || pos.equals(sensors[13])) {
        System.out.println("Train #" + drv.trainId + " wants lock #" + 6);
        drv.waitFor(locks[6]);
        System.out.println("Train #" + drv.trainId + " got lock #" + 6);
        if (pos.equals(sensors[12])) {
          switches[2].turn_left(drv.tsi);
        } else {
          switches[2].turn_right(drv.tsi);
        }
        return false;
      }

      if (pos.equals(sensors[15])) {
        if (locks[7].tryAcquire()) {
          switches[3].turn_left(drv.tsi);
        } else {
          if (!locks[8].tryAcquire()) {
            throw new AssertionError("Both L4 and L5 locked! I'm starving!");
          }
          switches[3].turn_right(drv.tsi);
        }
        return false;
      }

      if (pos.equals(sensors[16]) || pos.equals(sensors[17])) {
        System.out.println("Train #" + drv.trainId + " releasing lock #" + 6);
        locks[6].release();
        return false;
      }


      if (pos.equals(sensors[18]) || pos.equals(sensors[19])) {
        drv.stop();
        Thread.sleep(1000 + 20 * Math.abs(drv.speed));
        drv.reverse();
        return true;
      }
      return false;
    }

    @Override
    public boolean on_exit_sensor(SensorPos pos, TrainDriver drv) throws CommandException {
      if (pos.equals(sensors[6])) {
        locks[0].release();
        return false;
      }
      if (pos.equals(sensors[7])) {
        locks[1].release();
      }
      if (pos.equals(sensors[12])) {
        locks[4].release();
        return false;
      }

      if (pos.equals(sensors[13])) {
        locks[5].release();
        return false;
      }

      return false;
    }
  }

  static class NorthBoundBrain implements TrainDriver.Brain {
    @Override
    public String adjective() {
      return "north-bound";
    }

    @Override
    public boolean on_enter_sensor(SensorPos pos, TrainDriver drv) throws CommandException, InterruptedException {
      if (pos.equals(sensors[16]) || pos.equals(sensors[17])) {
        System.out.println("Train #" + drv.trainId + " wants lock #" + 6);
        drv.waitFor(locks[6]);
        System.out.println("Train #" + drv.trainId + " got lock #" + 6);
        if (pos.equals(sensors[16])) {
          switches[3].turn_left(drv.tsi);
        } else {
          switches[3].turn_right(drv.tsi);
        }
        return false;
      }

      if (pos.equals(sensors[14])) {
        if (locks[4].tryAcquire()) {
          switches[2].turn_left(drv.tsi);
        } else {
          if (!locks[5].tryAcquire()) {
            throw new AssertionError("Both L4 and L5 locked! I'm starving!");
          }
          switches[2].turn_right(drv.tsi);
        }
        return false;
      }

      if (pos.equals(sensors[12]) || pos.equals(sensors[13])) {
        System.out.println("Train #" + drv.trainId + " releasing lock #" + 6);
        locks[6].release();
        return false;
      }

      if (pos.equals(sensors[10]) || pos.equals(sensors[11])) {
        drv.waitFor(locks[3]);
        if (pos.equals(sensors[10])) {
          switches[1].turn_right(drv.tsi);
        } else {
          switches[1].turn_left(drv.tsi);
        }
        return false;
      }

      if (pos.equals(sensors[8])) {
        if (locks[1].tryAcquire()) {
          switches[0].turn_left(drv.tsi);
        } else {
          if (!locks[0].tryAcquire()) {
            throw new AssertionError("Both L4 and L5 locked! I'm starving!");
          }
          switches[0].turn_right(drv.tsi);
        }
        return false;
      }

      if (pos.equals(sensors[6]) || pos.equals(sensors[7])) {
        locks[3].release();
        return false;
      }

      if (pos.equals(sensors[2]) || pos.equals(sensors[3])) {
        locks[2].release();
        return false;
      }

      if (pos.equals(sensors[4]) || pos.equals(sensors[5])) {
        drv.waitFor(locks[2]);
        return false;
      }

      if (pos.equals(sensors[0]) || pos.equals(sensors[1])) {
        drv.stop();
        Thread.sleep(1000 + 20 * Math.abs(drv.speed));
        drv.advance();
        return true;
      }
      return false;
    }

    @Override
    public boolean on_exit_sensor(SensorPos pos, TrainDriver drv) throws CommandException {
      if (pos.equals(sensors[16])) {
        locks[7].release();
        return false;
      }
      if (pos.equals(sensors[17])) {
        locks[8].release();
        return false;
      }
      if (pos.equals(sensors[10])) {
        locks[4].release();
        return false;
      }

      if (pos.equals(sensors[11])) {
        locks[5].release();
        return false;
      }
      return false;
    }

  }
}

class help {
  public static <T> void print(T... args) {
    for(T pts: args) {
      System.out.print(pts);
      System.out.print(" ");
    }
    System.out.println();
  }
}

class SensorPos extends Pos {
  public SensorPos(int x, int y) {
    super(x, y);
  }
  public SensorPos(SensorEvent event) {
    super(event.getXpos(), event.getYpos());
  }
}

class SwitchPos extends Pos {
  public SwitchPos(int x, int y) {
    super(x, y);
  }

  public void turn_left(TSimInterface tsi) throws CommandException {
    tsi.setSwitch(x, y, TSimInterface.SWITCH_LEFT);
  }

  public void turn_right(TSimInterface tsi) throws CommandException {
    tsi.setSwitch(x, y, TSimInterface.SWITCH_RIGHT);
  }
}

class Pos {
  int x;
  int y;

  public Pos(int x, int y) {
    this.x = x;
    this.y = y;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SensorPos sensorPos = (SensorPos) o;
    return x == sensorPos.x &&
            y == sensorPos.y;
  }

  @Override
  public int hashCode() {
    return Objects.hash(x, y);
  }

  @Override
  public String toString() {
    return "SensorPos{" +
            "x=" + x +
            ", y=" + y +
            '}';
  }
}