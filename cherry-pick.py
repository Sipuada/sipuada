import fileinput, os, sys

target_line = int(sys.argv[1])

walk = 0
for line in fileinput.input(['-']):
    line = line.strip()
    if (line.startswith("#")):
        continue
    elif (len(line) == 0):
        continue
    elif (line.startswith("Author:")):
        continue
    elif (line.startswith("Merge:")):
        continue
    if (walk == target_line):
        print "~) execute:", line
        os.system(line)
        print "~) done. up next:", walk + 1

        os.system("grep -R 'sendOptionsRequest' src")
        os.system("grep -R 'handleOptionsRequest' src")
        os.system("grep -R 'handleOptionsResponse' src")
        os.system("grep -R 'sendInfoRequest' src")
        os.system("grep -R 'handleInfoRequest' src")
        os.system("grep -R 'handleInfoResponse' src")
        os.system("grep -R 'sendMessageRequest' src")
        os.system("grep -R 'handleMessageRequest' src")
        os.system("grep -R 'handleMessageResponse' src")
        sys.exit(0)
    walk += 1
