/**
 * FLASHYWRAPPERS
 *   
 * @author Pavel Langweil
 * @version 2.55
 *
 * Custom exception so we can send errors to AS3 easier
 *
 */

#include "FW_exception.h"

MyException::MyException(std::string ss) : s(ss) {}
MyException::~MyException() throw () {} // Updated
const char* MyException::what() const throw() { return s.c_str(); }
