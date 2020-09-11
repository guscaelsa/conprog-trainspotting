import TSim.CommandException;
import TSim.SensorEvent;
import TSim.TSimInterface;

public class TestMap {
    public static String filename = "simplest";

    EastBoundBrain brainA = new EastBoundBrain();
    WestBoundBrain brainB = new WestBoundBrain();

    static final SensorPos sr_left = new SensorPos(8,8);
    static final SensorPos sr_right = new SensorPos(12,9);
    static final SensorPos sr_stationLeft = new SensorPos(3,8);
    static final SensorPos sr_stationRight = new SensorPos(16,9);

    static final SwitchPos sw_left = new SwitchPos(9,8);
    static final SwitchPos sw_right = new SwitchPos(11,9);

    static class EastBoundBrain implements TrainDriver.Brain {
        @Override
        public String adjective() {
            return "east-bound";
        }

        @Override
        public boolean on_exit_sensor(SensorPos pos, TrainDriver drv) throws CommandException {
            if (pos.equals(sr_left)) {
                sw_right.turn_right(drv.tsi);
                return false;
            }
            return false;
        }

        @Override
        public boolean on_enter_sensor(SensorPos pos, TrainDriver drv) throws CommandException, InterruptedException {
            if (pos.equals(sr_left)) {
                sw_left.turn_left(drv.tsi);
                return false;
            }

            if (pos.equals(sr_stationRight)) {
                drv.stop();
                Thread.sleep(1000 + 20 * Math.abs(drv.speed));
                drv.reverse();
                return true;
            }
            return false;
        }
    }

    static class WestBoundBrain implements TrainDriver.Brain {
        @Override
        public String adjective() {
            return "west-bound";
        }

        @Override
        public boolean on_exit_sensor(SensorPos pos, TrainDriver drv) throws CommandException {
            if (pos.equals(sr_right)) {
                sw_left.turn_right(drv.tsi);
                return false;
            }
            return false;
        }

        @Override
        public boolean on_enter_sensor(SensorPos pos, TrainDriver drv) throws CommandException, InterruptedException {
            if (pos.equals(sr_right)) {
                sw_right.turn_left(drv.tsi);
                return false;
            }

            if (pos.equals(sr_stationLeft)) {
                drv.stop();
                Thread.sleep(1000 + 20 * Math.abs(drv.speed));
                drv.reverse();
                return true;
            }
            return false;
        }
    }
}
