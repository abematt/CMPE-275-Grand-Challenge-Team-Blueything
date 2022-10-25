# Queue Toys

A set of tests to help demonstrate concurrency and queue concepts.

## Examples

The following are provided. This is work in progress and you can contribute to the examples!

   - AsArray demonstrates caching issues when using an non-MT-safe container.
   - AsQueue replaces the array with a MT-safe class
   - AsMonitor uses a third entity to check on work
   
 ## Questions
 
   - What is the weakness of each approach?
   - Why does each monitor test have a different result (sum)? 
   - How would you improve the monitor example?
   - What changes need to be made to simulate three processes (A->B-C)?
   - How would an instance of Take reject work?
   - Likewise, how would Take put work back onto the queue?