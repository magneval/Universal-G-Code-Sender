/*
    Copyright 2017 Will Winder

    This file is part of Universal Gcode Sender (UGS).

    UGS is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    UGS is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with UGS.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.willwinder.ugs.platform.probe;

import static com.willwinder.ugs.platform.probe.ProbeService.retractDistance;
import com.willwinder.ugs.platform.probe.ProbeService.ProbeContext;
import com.willwinder.universalgcodesender.listeners.ControllerStatus;
import com.willwinder.universalgcodesender.model.BackendAPI;
import com.willwinder.universalgcodesender.model.Position;
import com.willwinder.universalgcodesender.model.UGSEvent;
import com.willwinder.universalgcodesender.model.UnitUtils.Units;
import static com.willwinder.universalgcodesender.model.WorkCoordinateSystem.G54;
import static com.willwinder.universalgcodesender.model.WorkCoordinateSystem.G55;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;

/**
 *
 * @author wwinder
 */
public class ProbeServiceTest {
    
    BackendAPI backend = Mockito.mock(BackendAPI.class);

    @Test
    public void testProbeServiceZ() throws Exception {
        doReturn(true).when(backend).isIdle();

        ProbeContext pc = new ProbeContext(1, new Position(5, 5, 5, Units.MM), 10, 10, 10., 1, 1, 1, 100, 25, 5, Units.INCH, G54);
        testZProbe(pc, pc.zSpacing < 0);

        pc = new ProbeContext(1, new Position(5, 5, 5, Units.MM), 10, 10, -10., 1, 1, 1, 100, 25, 5, Units.INCH, G54);
        testZProbe(pc, pc.zSpacing < 0);
    }

    private void testZProbe(ProbeContext pc, boolean finalRetract) throws Exception {
        ProbeService ps = new ProbeService(backend);

        ps.performZProbe(pc);

        Position probeZ = new Position(5, 5, 3, Units.MM);
        ps.UGSEvent(new UGSEvent(probeZ));
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_SENDING));
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_IDLE));
        ps.UGSEvent(new UGSEvent(probeZ));

        InOrder order = inOrder(backend);

        order.verify(backend, times(1)).probe("Z", pc.feedRate, pc.zSpacing, pc.units);
        order.verify(backend, times(1)).sendGcodeCommand(true, "G91 G20 G0 Z" + retractDistance(pc.zSpacing));
        order.verify(backend, times(1)).probe("Z", pc.feedRateSlow, pc.zSpacing, pc.units);
        order.verify(backend, times(1)).sendGcodeCommand(true, "G10 L20 P1 Z1.0");
        if (finalRetract) {
            order.verify(backend, times(1)).sendGcodeCommand(true, "G90 G20 G0 Z" + (pc.retractHeight + pc.zOffset));
        }
    }

    @Test
    public void testProbeServiceOutside() throws Exception {
        doReturn(true).when(backend).isIdle();

        ProbeService ps = new ProbeService(backend);

        ProbeContext pc = new ProbeContext(1, new Position(5, 5, 5, Units.MM), 10, 10, 0., 1, 1, 1, 100, 25, 5, Units.MM, G55);
        ps.performOutsideCornerProbe(pc);

        Position probeY = new Position(2.0, 2.0, 0, Units.MM);
        Position probeX = new Position(1.0, 1.0, 0, Units.MM);

        // Events to transition between states.
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_SENDING));
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_IDLE));
        ps.UGSEvent(new UGSEvent(probeY));
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_SENDING));
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_IDLE));
        ps.UGSEvent(new UGSEvent(probeY));
        ps.UGSEvent(new UGSEvent(new ControllerStatus(null, probeY,null,0.,0.,null,null,null,null)));
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_SENDING));
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_IDLE));
        ps.UGSEvent(new UGSEvent(probeX));
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_SENDING));
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_IDLE));
        ps.UGSEvent(new UGSEvent(probeX));
        ps.UGSEvent(new UGSEvent(new ControllerStatus(null, probeX,null,0.,0.,null,null,null,null)));

        InOrder order = inOrder(backend);
        // probe Y axis
        order.verify(backend, times(1)).sendGcodeCommand(true, "G91 G21 G0 X" + pc.xSpacing);
        order.verify(backend, times(1)).probe("Y", pc.feedRate, pc.ySpacing, pc.units);
        order.verify(backend, times(1)).sendGcodeCommand(true, "G91 G21 G0 Y" + retractDistance(pc.ySpacing));
        order.verify(backend, times(1)).probe("Y", pc.feedRateSlow, pc.ySpacing, pc.units);
        order.verify(backend, times(1)).sendGcodeCommand(true, "G91 G21 G0 Y" + (pc.startPosition.y-probeY.y));
        order.verify(backend, times(1)).sendGcodeCommand(true, "G91 G21 G0 X" + -pc.xSpacing);

        // probe X axis
        order.verify(backend, times(1)).sendGcodeCommand(true, "G91 G21 G0 Y" + pc.ySpacing);
        order.verify(backend, times(1)).probe("X", pc.feedRate, pc.xSpacing, pc.units);
        order.verify(backend, times(1)).sendGcodeCommand(true, "G91 G21 G0 X" + retractDistance(pc.ySpacing));
        order.verify(backend, times(1)).probe("X", pc.feedRateSlow, pc.xSpacing, pc.units);
        order.verify(backend, times(1)).sendGcodeCommand(true, "G91 G21 G0 X" + (pc.startPosition.x-probeX.x));
        order.verify(backend, times(1)).sendGcodeCommand(true, "G91 G21 G0 Y" + -pc.xSpacing);

        // Verify the correct offset
        double radius = pc.probeDiameter / 2;
        double xDir = ((pc.xSpacing > 0) ? -1 : 1);
        double yDir = ((pc.ySpacing > 0) ? -1 : 1);
        double xProbeOffset = pc.startPosition.x - probeX.x + xDir * (radius + Math.abs(pc.xOffset));
        double yProbeOffset = pc.startPosition.y - probeY.y + yDir * (radius + Math.abs(pc.yOffset));

        order.verify(backend, times(1)).sendGcodeCommand(true, "G10 L20 P2 X" + xProbeOffset + " Y" + yProbeOffset);
    }

    @Test
    public void testProbeServiceXYZ() throws Exception {
        doReturn(true).when(backend).isIdle();

        ProbeService ps = new ProbeService(backend);

        ProbeContext pc = new ProbeContext(1, new Position(5, 5, 5, Units.MM), 10, 10, 0., 1, 1, 1, 100, 25, 5, Units.MM, G55);
        ps.performXYZProbe(pc);

        Position probeY = new Position(2.0, 2.0, 0, Units.MM);
        Position probeX = new Position(1.0, 1.0, 0, Units.MM);
        Position probeZ = new Position(0., 0., 3.0, Units.MM);

        // Events to transition between states.
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_SENDING));
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_IDLE));
        ps.UGSEvent(new UGSEvent(probeZ));
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_SENDING));
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_IDLE));
        ps.UGSEvent(new UGSEvent(probeZ));
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_SENDING));
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_IDLE));
        ps.UGSEvent(new UGSEvent(probeX));
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_SENDING));
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_IDLE));
        ps.UGSEvent(new UGSEvent(probeX));
        ps.UGSEvent(new UGSEvent(new ControllerStatus(null, probeX,null,0.,0.,null,null,null,null)));
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_SENDING));
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_IDLE));
        ps.UGSEvent(new UGSEvent(probeY));
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_SENDING));
        ps.UGSEvent(new UGSEvent(UGSEvent.ControlState.COMM_IDLE));
        ps.UGSEvent(new UGSEvent(probeY));
        ps.UGSEvent(new UGSEvent(new ControllerStatus(null, probeY,null,0.,0.,null,null,null,null)));

        // TODO: Finish all these
        InOrder order = inOrder(backend);

        // Probe Z axis
        order.verify(backend, times(1)).probe("Z", pc.feedRate, pc.zSpacing, pc.units);
        order.verify(backend, times(1)).sendGcodeCommand(true, "G91 G21 G0 Z" + retractDistance(pc.zSpacing));
        order.verify(backend, times(1)).probe("Z", pc.feedRateSlow, pc.zSpacing, pc.units);
        order.verify(backend, times(1)).sendGcodeCommand(true, "G91 G21 G0 Z" + (pc.startPosition.z-probeZ.z));
        order.verify(backend, times(1)).sendGcodeCommand(true, "G91 G21 G0 X" + -pc.xSpacing);
        order.verify(backend, times(1)).sendGcodeCommand(true, "G91 G21 G0 Z" + pc.zSpacing);

        // probe X axis
        order.verify(backend, times(1)).probe("X", pc.feedRate, pc.xSpacing, pc.units);
        order.verify(backend, times(1)).sendGcodeCommand(true, "G91 G21 G0 X" + retractDistance(pc.ySpacing));
        order.verify(backend, times(1)).probe("X", pc.feedRateSlow, pc.xSpacing, pc.units);
        order.verify(backend, times(1)).sendGcodeCommand(true, "G91 G21 G0 X" + (pc.startPosition.x - pc.xSpacing - probeX.x));
        order.verify(backend, times(1)).sendGcodeCommand(true, "G91 G21 G0 Y" + -pc.ySpacing);
        order.verify(backend, times(1)).sendGcodeCommand(true, "G91 G21 G0 X" + pc.xSpacing);

        // probe Y axis
        order.verify(backend, times(1)).probe("Y", pc.feedRate, pc.ySpacing, pc.units);
        order.verify(backend, times(1)).sendGcodeCommand(true, "G91 G21 G0 Y" + retractDistance(pc.ySpacing));
        order.verify(backend, times(1)).probe("Y", pc.feedRateSlow, pc.ySpacing, pc.units);
        order.verify(backend, times(1)).sendGcodeCommand(true, "G91 G21 G0 Y" + (pc.startPosition.y - pc.ySpacing - probeY.y));
        order.verify(backend, times(1)).sendGcodeCommand(true, "G91 G21 G0 Y" + pc.ySpacing);


        // Verify the correct offset
        double radius = pc.probeDiameter / 2;
        double xDir = ((pc.xSpacing > 0) ? -1 : 1);
        double yDir = ((pc.ySpacing > 0) ? -1 : 1);
        double xProbeOffset = pc.startPosition.x - probeX.x + xDir * (radius + Math.abs(pc.xOffset));
        double yProbeOffset = pc.startPosition.y - probeY.y + yDir * (radius + Math.abs(pc.yOffset));
        double zProbeOffset = pc.startPosition.z - probeZ.z;

        order.verify(backend, times(1)).sendGcodeCommand(true,
                "G10 L20 P2 X" + xProbeOffset + " Y" + yProbeOffset + " Z" + zProbeOffset);
    }
}
