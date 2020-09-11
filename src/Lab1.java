import TSim.*;

import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

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

  /*
  Some railway terminology:

  Turnout = Railroad switch

  ↓ Trailing ↓
  || //
  ||//
  |#/
  ||
  ||
  ↑ Facing ↑
   */

  public void waitFor(Semaphore lock) throws CommandException {
    if (!lock.tryAcquire()) {
      stop();
      lock.acquireUninterruptibly();
      drive();
    }
  }

  public void facingWait(Semaphore lockA, Consumer<TSimInterface> turnA, Semaphore lockB, Consumer<TSimInterface> turnB) {
    if (lockA.tryAcquire()) {
      turnA.accept(tsi);
    } else {
      if (!lockB.tryAcquire()) {
        throw new AssertionError("Both locks of fork are held.");
      }
      turnB.accept(tsi);
    }
  }

  public void trailingWait(Semaphore lock, boolean turnRight, SwitchPos turnout) throws CommandException {
      waitFor(lock);
      if (turnRight) {
          turnout.turn_right(tsi);
      } else {
          turnout.turn_left(tsi);
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

  void drive() throws CommandException {
    tsi.setSpeed(trainId, speed);
  }

  void stop() throws CommandException {
    tsi.setSpeed(trainId, 0);
  }

  void reverse() throws CommandException {
    speed = -1 * speed;
    drive();
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
          drv.trailingWait(locks[3], pos.equals(sensors[6]), switches[0]);
        return false;
      }

      if (pos.equals(sensors[9])) {
          drv.facingWait(locks[4], switches[1]::turn_right, locks[5], switches[1]::turn_left);
        return false;
      }

      if (pos.equals(sensors[10]) || pos.equals(sensors[11])) {
        locks[3].release();
        return false;
      }

      if (pos.equals(sensors[12]) || pos.equals(sensors[13])) {
          drv.trailingWait(locks[6], pos.equals(sensors[13]), switches[2]);
        return false;
      }

      if (pos.equals(sensors[15])) {
          drv.facingWait(locks[7], switches[3]::turn_left, locks[8], switches[3]::turn_right);
        return false;
      }

      if (pos.equals(sensors[16]) || pos.equals(sensors[17])) {
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
        return false;
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
          drv.trailingWait(locks[6], pos.equals(sensors[17]), switches[3]);
        return false;
      }

      if (pos.equals(sensors[14])) {
          drv.facingWait(locks[4], switches[2]::turn_left, locks[5], switches[2]::turn_right);
        return false;
      }

      if (pos.equals(sensors[12]) || pos.equals(sensors[13])) {
        locks[6].release();
        return false;
      }

      if (pos.equals(sensors[10]) || pos.equals(sensors[11])) {
          drv.trailingWait(locks[3], pos.equals(sensors[10]), switches[1]);
        return false;
      }

      if (pos.equals(sensors[8])) {
          drv.facingWait(locks[1], switches[0]::turn_left, locks[0], switches[0]::turn_right);
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
        drv.reverse();
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

  public void turn_left(TSimInterface tsi) {
    try {
      tsi.setSwitch(x, y, TSimInterface.SWITCH_LEFT);
    } catch (CommandException e) {
      throw new AssertionError("Invalid switch: " + x + ", " + y);
    }
  }

  public void turn_right(TSimInterface tsi)  {
    try {
      tsi.setSwitch(x, y, TSimInterface.SWITCH_RIGHT);
    } catch (CommandException e) {
      throw new AssertionError("Invalid switch: " + x + ", " + y);
    }
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
