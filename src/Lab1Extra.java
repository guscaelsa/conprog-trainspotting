import TSim.*;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class Lab1Extra {
  public static MapExtra map = new MapExtra();

  public Lab1Extra(int speed1, int speed2) {
    TSimInterface tsi = TSimInterface.getInstance();

    new Thread(new TrainDriverExtra(tsi, 1, speed1, map.brainA, map.brainB)).start();
    new Thread(new TrainDriverExtra(tsi, 2, speed2, map.brainB, map.brainA)).start();
  }
}

class MonitorLock {
  private final Lock l = new ReentrantLock();
  private final Condition available = l.newCondition();
  private boolean occupied;

  MonitorLock(boolean initial) {
    occupied = initial;
  }

  public boolean tryEnter() {
    l.lock();
    try {
      if (occupied) {
        return false;
      } else {
        occupied = true;
        return true;
      }
    } finally {
      l.unlock();
    }
  }

  public void waitEnter() {
    l.lock();
    try {
      while (occupied) {
        available.awaitUninterruptibly();
      }
      occupied = true;
    } finally {
      l.unlock();
    }
  }

  public void exit() {
    l.lock();
    try {
      if (!occupied) {
        throw new AssertionError("Monitor exited while not occupied");
      }
      occupied = false;
      available.signal();
    } finally {
      l.unlock();
    }
  }
}

class TrainDriverExtra extends TrainDriver {
  public TrainDriverExtra(TSimInterface tsi, int trainId, int speed, Brain a, Brain b) {
    super(tsi, trainId, speed, a, b);
  }
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

  public void waitFor(MonitorLock lock) throws CommandException {
    if (!lock.tryEnter()) {
      stop();
      lock.waitEnter();
      drive();
    }
  }

  public void facingWait(MonitorLock lockA, Consumer<TSimInterface> turnA, MonitorLock lockB, Consumer<TSimInterface> turnB) {
    if (lockA.tryEnter()) {
      turnA.accept(tsi);
    } else {
      if (!lockB.tryEnter()) {
        throw new AssertionError("Both locks of fork are occupied.");
      }
      turnB.accept(tsi);
    }
  }

  public void trailingWait(MonitorLock lock, boolean turnRight, SwitchPos turnout) throws CommandException {
      waitFor(lock);
      if (turnRight) {
          turnout.turn_right(tsi);
      } else {
          turnout.turn_left(tsi);
      }
  }
}

class MapExtra {
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

  static final MonitorLock[] locks = {
          new MonitorLock(true), // a/c
          new MonitorLock(false), // b/d
          new MonitorLock(false), // crossroad
          new MonitorLock(false), // e
          new MonitorLock(false), // f
          new MonitorLock(false), // g
          new MonitorLock(false), // h
          new MonitorLock(true), // i
          new MonitorLock(false), // j
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
        locks[2].exit();
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
        locks[3].exit();
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
        locks[6].exit();
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
        locks[0].exit();
        return false;
      }
      if (pos.equals(sensors[7])) {
        locks[1].exit();
        return false;
      }
      if (pos.equals(sensors[12])) {
        locks[4].exit();
        return false;
      }

      if (pos.equals(sensors[13])) {
        locks[5].exit();
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
        locks[6].exit();
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
        locks[3].exit();
        return false;
      }

      if (pos.equals(sensors[2]) || pos.equals(sensors[3])) {
        locks[2].exit();
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
        locks[7].exit();
        return false;
      }
      if (pos.equals(sensors[17])) {
        locks[8].exit();
        return false;
      }
      if (pos.equals(sensors[10])) {
        locks[4].exit();
        return false;
      }

      if (pos.equals(sensors[11])) {
        locks[5].exit();
        return false;
      }
      return false;
    }
  }
}
