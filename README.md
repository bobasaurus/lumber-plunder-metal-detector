![logo](https://github.com/bobasaurus/lumber-plunder-metal-detector/blob/main/images/lumber%20plunder%20logo.png?raw=true)

A VLF induction balance metal detector with a wooden body using an android smartphone for DSP and display.  

![wooden coil](https://github.com/bobasaurus/lumber-plunder-metal-detector/blob/main/images/detector%20coil.jpg?raw=true)

This project is influenced by and tries to improve upon similar DIY metal detectors, including the following:
* Neco Smart Hunter EN:  https://neco-desarrollo.es/smart-hunter
* The vistac2000 pc-based metal detector with usb interface: https://www.geotech1.com/forums/showthread.php?14102-Let-s-made-a-PC-base-metal-detector-with-usb-interface-!!!
* The TGSL (for the coil design): https://www.geotech1.com/forums/showthread.php?15710-TGSL-Complete-Details

![detector cat](https://github.com/bobasaurus/lumber-plunder-metal-detector/blob/main/images/D82muwP.jpg?raw=true)

The first version of the schematic is available here:

https://github.com/bobasaurus/lumber-plunder-metal-detector/blob/main/images/lumber%20plunder%20metal%20detector%20schematic%20v1.pdf

The android app is still in development, I'm learning as I go.  

I followed the coil design and wrapping guides for the TGSL linked above, though using a thicker magnet wire I happened to have on hand (and using fewer wraps to reduce weight):

![wrapping jig](https://github.com/bobasaurus/lumber-plunder-metal-detector/blob/main/images/coil%20winding.jpg)

After wrapping, the coils were stiffened with dental floss knots and some waterlox wood finish.  They were then shielded by wrapping sparsely with bare copper wire then coated with a mixture of polyurethane and graphite powder.  Finally, the coils were wrapped in electrical tape to insulate.  

After determining the correct amount of overlap to cancel out the transmit signal on the receive coil, I designed and built a wooden case for the coils with dowel adjusters to keep the receive coil nulled while the epoxy cured (measured with an oscilloscope while transmitting a 15 kHz sine wave on the tx coil):

![coil case](https://github.com/bobasaurus/lumber-plunder-metal-detector/blob/main/images/coil%20case.jpg)

I'm using a shielded USB cable to connect the coils to the control circuitry.  Then a smartphone app generates the transmit signal and receives the return signal using the 3.5 mm audio jack.  

2022/2/10:  I found a problem with the schematic, I needed to put a 4.7k resistor from the microphone input to ground in order for my phone to detect it as a "headset" and change the audio input device from the internal mic to the cable/plug.  I'll update it soon.  
