import fileinput, os, sys

target_line = int(sys.argv[1])

walk = 0
for line in fileinput.input(['-']):
    line = line.strip()
    if (line.startswith("#")):
        continue
    elif (len(line) == 0):
        continue
    if (walk == target_line):
        print "~) execute:", line
        os.system(line)
        print "~) done. up next:", walk + 1
        sys.exit(0)
    walk += 1
