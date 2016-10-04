#!/bin/bash

CRFSUITE="$HOME/bin/crfsuite-0.12/bin/crfsuite"
java -jar target/starter-0.1-SNAPSHOT.jar --crfsuite=${CRFSUITE} --class=PER --trainingStart=5
