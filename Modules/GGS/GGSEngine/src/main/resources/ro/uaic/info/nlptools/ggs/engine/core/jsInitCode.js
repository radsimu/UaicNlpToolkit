/**************************************************************************
 * Copyright © 2017 Radu Simionescu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *************************************************************************/

function clone(obj){

	var clonedObjectsArray = [];
	var originalObjectsArray = []; //used to remove the unique ids when 
	var next_objid = 0;
	
	function objectId(obj) {
		if (obj == null) return null;
		if (obj.__obj_id == undefined){
			obj.__obj_id = next_objid++;
			originalObjectsArray[obj.__obj_id] = obj;
		}
		return obj.__obj_id;
	}

	function cloneRecursive(obj) {
		if (null == obj || typeof obj == "string" || typeof obj == "number" || typeof obj == "boolean") return obj;

		// Handle Date
		if (obj instanceof Date) {
			var copy = new Date();
			copy.setTime(obj.getTime());
			return copy;
		}

		// Handle Array
		if (obj instanceof Array) {
			var copy = [];
			for (var i = 0; i < obj.length; ++i) {
				copy[i] = cloneRecursive(obj[i]);
			}
			return copy;
		}

		// Handle Object
		if (obj instanceof Object) {
			if (clonedObjectsArray[objectId(obj)] != undefined)
				return clonedObjectsArray[objectId(obj)];
			
			var copy;
			if (obj instanceof Function)//Handle Function
				copy = function(){return obj.apply(this, arguments);};
			else
				copy = {};
			
			clonedObjectsArray[objectId(obj)] = copy;
			
			for (var attr in obj)
				if (attr != "__obj_id" && obj.hasOwnProperty(attr)){
					copy[attr] = cloneRecursive(obj[attr]);					
				}
			return copy;
		}	
		
		throw new Error("Unable to clone obj! Its type isn't supported.");
	}
	var cloneObj = cloneRecursive(obj);
	
	for (var i = 0; i < originalObjectsArray.length; i++)
	{
		delete originalObjectsArray[i].__obj_id;
	};
	
	return cloneObj;
}

function restoreGraph(graph, graphBackup){
	clearGraph(graph);
	
	for (var attr in graphBackup){
		if (graphBackup.hasOwnProperty(attr))
    	    graph[attr] = graphBackup[attr];
	}
}

function clearGraph(graph){
	for (var attr in graph){
		if (graph.hasOwnProperty(attr) && attr != "jsCode" )
			delete graph[attr];
	}
}