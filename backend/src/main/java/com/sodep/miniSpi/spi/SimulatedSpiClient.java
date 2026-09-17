package com.sodep.miniSpi.spi;

import org.springframework.stereotype.Service;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SimulatedSpiClient implements SpiClient{

    private static final double FAILURE_RATE =  0.20;
    @Override
    public boolean processTransfer(Long transferId) {
        return ThreadLocalRandom.current().nextDouble() >= FAILURE_RATE;
    }
}
