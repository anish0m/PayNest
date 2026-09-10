/**
 * Business ruleset like :
 * →"This email is already registered" needs every other user.
 * →"This password was one of your last five" needs the database.
 * →A User can check its own length and blankness — those stay in the model.
 * No HTTP: no HttpServletRequest, no status codes, no JSON.
 */
package com.paynest.user.service;
