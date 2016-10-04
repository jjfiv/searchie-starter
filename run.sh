#!/bin/bash

CRFSUITE="$HOME/bin/crfsuite-0.12/bin/crfsuite"

mvn install # download dependencies if needed & compile
java -jar target/starter-0.1-SNAPSHOT.jar --crfsuite=${CRFSUITE} --class=PER --trainingStart=5
