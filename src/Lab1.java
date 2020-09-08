import TSim.*;
import javafx.util.Pair;

import java.util.Objects;
import java.util.concurrent.Semaphore;

public class Lab1 {
  public static Map map = new Map();

  public Lab1(int speed1, int speed2) {
    TSimInterface tsi = TSimInterface.getInstance();

    new Thread(new TrainDriver(tsi, 1, speed1, map.brainA, map.brainB)).start();
    new Thread(new TrainDriver(tsi, 2, speed2, map.brainB, map.brainA)).start();
  }
}


class TrainDriver implements Runnable {
  protected int trainId;
  TSimInterface tsi;
  int speed;

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
    } else {
      tsi.setSpeed(trainId, -1 * speed);
    }
  }

  void stop() throws CommandException {
    tsi.setSpeed(trainId, 0);
  }

  void reverse() throws CommandException {
    if (trainId == 1) {
      tsi.setSpeed(trainId, -1 * speed);
    } else {
      tsi.setSpeed(trainId, speed);
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

  static final SwitchPos switches[] = {
          new SwitchPos(17, 7),
          new SwitchPos(15, 9),
          new SwitchPos(4, 9),
          new SwitchPos(3, 11),
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
    public boolean on_exit_sensor(SensorPos pos, TrainDriver drv) throws CommandException {
      if (pos.equals(sr_topTT) || pos.equals(sr_botTT)) {
        sem_middle.release();
      }

      if (pos.equals(sr_botSS)) {
        sem_botS.release();
      }
      return false;
    }

    @Override
    public boolean on_enter_sensor(SensorPos pos, TrainDriver drv) throws CommandException, InterruptedException {
      if (pos.equals(sr_topSS) || pos.equals(sr_botSS)) {
        if (!sem_middle.tryAcquire()) {
          drv.stop();
          sem_middle.acquireUninterruptibly();
          drv.advance();
        }

        if (pos.equals(sr_topSS)) {
          switches[0].turn_right(drv.tsi);
        } else {
          switches[0].turn_left(drv.tsi);
        }
        switches[1].turn_right(drv.tsi);
        switches[2].turn_left(drv.tsi);
        switches[3].turn_right(drv.tsi);
        return false;
      }

      if (pos.equals(sr_enterT)) {
        if (sem_topT.tryAcquire()) {
          switches[3].turn_left(drv.tsi);
        } else {
          switches[3].turn_right(drv.tsi);
        }
      }

      if (pos.equals(sr_botT) || pos.equals(sr_topT)) {
        drv.stop();
        Thread.sleep(1000 + 20 * Math.abs(drv.speed));
        drv.reverse();
        return true;
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
    public boolean on_exit_sensor(SensorPos pos, TrainDriver drv) throws CommandException {
      if (pos.equals(sr_topSS) || pos.equals(sr_botSS)) {
        sem_middle.release();
      }
      if (pos.equals(sr_topTT)) {
        sem_topT.release();
      }
      return false;
    }

    @Override
    public boolean on_enter_sensor(SensorPos pos, TrainDriver drv) throws CommandException, InterruptedException {
      if (pos.equals(sr_topTT) || pos.equals(sr_botTT)) {
        if (!sem_middle.tryAcquire()) {
          drv.stop();
          sem_middle.acquireUninterruptibly();
          drv.reverse();
        }

        if (pos.equals(sr_topTT)) {
          switches[3].turn_left(drv.tsi);
        } else {
          switches[3].turn_right(drv.tsi);
        }
        switches[0].turn_right(drv.tsi);
        switches[1].turn_right(drv.tsi);
        switches[2].turn_left(drv.tsi);
        return false;
      }

      if (pos.equals(sr_enterS)) {
        if (sem_botS.tryAcquire()) {
          switches[0].turn_left(drv.tsi);
        } else {
          switches[0].turn_right(drv.tsi);
        }
      }

      if (pos.equals(sr_botS) || pos.equals(sr_topS)) {
        drv.stop();
        Thread.sleep(1000 + 20 * Math.abs(drv.speed));
        drv.advance();
        return true;
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