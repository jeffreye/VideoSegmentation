# Introduction
This was the final project in CS576 at USC. For more details, see Video-SegmentationCompression.docx

Test cases are right [here](https://drive.google.com/file/d/0B6yPKfIXOM7PcHE2NHkyZWdJa1E/view?usp=sharing). 

# Usage
1) myencoder.exe input_video.rgb

	input_video.rgb is the input file to your encoder.
  
	(the output of this is a “input_video.cmp” layer wise compressed video  file)

2) mydecoder.exe input_video.cmp n1 n2 gazeControl
  
	n1 represents quantization step for foreground macroblocks

	n2 represents quantization step for background macroblocks

	gazeControl  - boolean value 0 or 1 to evaluate gaze control

	(decodes the compressed file as renders layers according to a quantized values)
