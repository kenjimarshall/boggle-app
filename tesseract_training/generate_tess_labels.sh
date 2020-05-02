#! /bin/bash

N=6
for i in `seq 0 $N`; do
	tesseract processed/boggle.$i.processed.jpg processed/boggle.$i.processed batch.nochop makebox
done	
