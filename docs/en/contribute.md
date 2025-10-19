**Design Principles**
1. Simplification.
	* Lightweight meta data, minimizing write operations，utilize cache;

	* Focus on high performance, with reliability ensured by backend object storage.

2. Modules are independent and do not interfere with each other, communicating only via HTTP interfaces.

	* PMS: Focuses on authentication and metadata management.

	* PCP: Focuses on the performance of file block uploads and downloads.

	* SDK: Handles concurrent file uploads and downloads.

3. Adhere to the current code style.


**Contribution Process**

1. Create a topic branch for your contribution based on the main branch.

2. Submit a Pull Request (PR) to the cache repository.

3. The PR must receive approval from at least one maintainer before it can be merged.