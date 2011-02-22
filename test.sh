# Hello
echo "Please be patient. This test will last at least 10 seconds"

# Create ring
# echo "Zeh master Haxxor" | java MulticastQueueImpl 1337 &

# The number of threads to be run
nthreads=5

function launchhaxxor()
{
    sleep $1; ( echo Haxxor$1;\
        sleep `echo $nthreads+1-$1 | bc`;\
        echo "I am 1337 Haxxor No. $1.";\
        sleep 1; echo quit) | java MulticastQueueImpl 1337 localhost `echo $1+1337 | bc` > /dev/null
}

for i in `seq $nthreads`
do launchhaxxor $i &
done
