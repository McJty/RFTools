package mcjty.rftools.compat.computers;

import li.cil.oc.api.Driver;
import mcjty.rftools.blocks.generator.CoalGeneratorConfiguration;
import mcjty.rftools.blocks.crafter.CrafterConfiguration;
import net.minecraftforge.fml.common.Optional;

public class OpenComputersIntegration {
    @Optional.Method(modid="opencomputers")
    public static void init() {
        Driver.add(new MachineInfuserDriver.OCDriver());
        Driver.add(new DialingDeviceDriver.OCDriver());
        Driver.add(new MatterReceiverDriver.OCDriver());
        Driver.add(new MatterTransmitterDriver.OCDriver());
        if(CoalGeneratorConfiguration.enabled.get())
            Driver.add(new CoalGeneratorDriver.OCDriver());
        Driver.add(new RFMonitorDriver.OCDriver());
        Driver.add(new LiquidMonitorDriver.OCDriver());
        Driver.add(new PowercellDriver.OCDriver());
        Driver.add(new PearlInjectorDriver.OCDriver());
        Driver.add(new EndergenicDriver.OCDriver());
        if(CrafterConfiguration.enabled.get())
            Driver.add(new CrafterDriver.OCDriver());
        Driver.add(new ElevatorDriver.OCDriver());
    }
}
