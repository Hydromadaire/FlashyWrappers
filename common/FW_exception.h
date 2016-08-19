/**
 * FLASHYWRAPPERS
 *   
 * @author Pavel Langweil
 * @version 2.55
 *
 * Custom exception so we can send errors to AS3 easier
 *
 */

#ifndef FW_EXCEPTION_H
#define FW_EXCEPTION_H

#include <stdlib.h>
#include <exception>
#include <string>
#include <signal.h>

class MyException : public std::exception {
   private:
	   std::string s;
   public:
	   MyException(std::string ss);
	   ~MyException() throw ();
	   virtual const char* what() const throw();
};

#endif