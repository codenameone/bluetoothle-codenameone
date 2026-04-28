#!/usr/bin/env python3
"""Deterministic Bumble BLE peripheral used by the Android E2E instrumentation test.

The test asserts the exact UUIDs and values defined here, so they are kept in
lock-step with the Java test (`BluetoothEmulatorEndToEndTest`). If you change a
UUID or value here, change it there too.

Bumble docs: https://google.github.io/bumble/
"""

from __future__ import annotations

import asyncio
import logging
import os
import sys

from bumble.device import Device
from bumble.gatt import (
    Characteristic,
    CharacteristicValue,
    Service,
)
from bumble.transport import open_transport_or_link

LOG = logging.getLogger("bumble_peripheral")

DEVICE_NAME = "BumbleSensor"

SERVICE_UUID = "0000a000-0000-1000-8000-00805f9b34fb"
READ_CHAR_UUID = "0000a001-0000-1000-8000-00805f9b34fb"
WRITE_CHAR_UUID = "0000a002-0000-1000-8000-00805f9b34fb"
NOTIFY_CHAR_UUID = "0000a003-0000-1000-8000-00805f9b34fb"

READ_VALUE = b"BUMBLE_OK"
NOTIFY_PAYLOAD = b"PING"
NOTIFY_INTERVAL_SECONDS = 1.0


class WriteSink:
    """Captures the most recent value written to the write characteristic so we
    can echo it back on subsequent reads. Lets the instrumentation test assert
    a real round-trip rather than just write success."""

    def __init__(self) -> None:
        self.last: bytes = b""

    def on_write(self, _connection, value: bytes) -> None:
        LOG.info("write received: %s", value)
        self.last = bytes(value)

    def on_read(self, _connection) -> bytes:
        return self.last


async def main() -> int:
    logging.basicConfig(
        level=os.environ.get("BUMBLE_LOG_LEVEL", "INFO"),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )

    transport_spec = os.environ.get("BUMBLE_TRANSPORT", "android-netsim")
    LOG.info("opening Bumble transport: %s", transport_spec)
    async with await open_transport_or_link(transport_spec) as transport:
        device = Device.with_hci(
            DEVICE_NAME,
            "F0:F0:F0:F0:F0:01",
            transport.source,
            transport.sink,
        )

        write_sink = WriteSink()

        notify_char = Characteristic(
            NOTIFY_CHAR_UUID,
            Characteristic.Properties.NOTIFY,
            Characteristic.READABLE,
            NOTIFY_PAYLOAD,
        )
        service = Service(
            SERVICE_UUID,
            [
                Characteristic(
                    READ_CHAR_UUID,
                    Characteristic.Properties.READ,
                    Characteristic.READABLE,
                    READ_VALUE,
                ),
                Characteristic(
                    WRITE_CHAR_UUID,
                    Characteristic.Properties.WRITE
                    | Characteristic.Properties.READ,
                    Characteristic.READABLE | Characteristic.WRITEABLE,
                    CharacteristicValue(read=write_sink.on_read, write=write_sink.on_write),
                ),
                notify_char,
            ],
        )
        device.add_service(service)

        await device.power_on()
        await device.start_advertising(auto_restart=True)
        LOG.info(
            "advertising as %s with service %s; readChar=%s writeChar=%s notifyChar=%s",
            DEVICE_NAME,
            SERVICE_UUID,
            READ_CHAR_UUID,
            WRITE_CHAR_UUID,
            NOTIFY_CHAR_UUID,
        )

        async def notify_loop() -> None:
            counter = 0
            while True:
                await asyncio.sleep(NOTIFY_INTERVAL_SECONDS)
                counter += 1
                payload = NOTIFY_PAYLOAD + bytes([counter & 0xFF])
                notify_char.value = payload
                for connection in list(device.connections.values()):
                    try:
                        await device.notify_subscriber(connection, notify_char)
                    except Exception as ex:  # pylint: disable=broad-except
                        LOG.warning("notify failed: %s", ex)

        notify_task = asyncio.create_task(notify_loop())
        try:
            await asyncio.Event().wait()
        finally:
            notify_task.cancel()
    return 0


if __name__ == "__main__":
    try:
        sys.exit(asyncio.run(main()))
    except KeyboardInterrupt:
        sys.exit(0)
