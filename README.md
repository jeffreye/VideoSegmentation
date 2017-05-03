# USC-CS576-Final
Video compression encoder/decoder and video player with gaze control

# Usage
* Copy your videos(.rgb) to the root folder
* Edit program arguments in the configuration
* Run

# Input Arguments
1) myencoder.exe input_video.rgb

	input_video.rgb is the input file to your encoder.
  
	(the output of this is a “input_video.cmp” layer wise compressed video  file)

2) mydecoder.exe input_video.cmp n1 n2 gazeControl
  
	n1 represents quantization step for foreground macroblocks

	n2 represents quantization step for background macroblocks

	gazeControl  - boolean value 0 or 1 to evaluate gaze control

	(decodes the compressed file as renders layers according to a quantized values)
