package com.marginallyclever.robotoverlord.mesh.load;

import com.jogamp.opengl.GL2;
import com.marginallyclever.robotoverlord.mesh.Mesh;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

// see https://en.wikipedia.org/wiki/Wavefront_.obj_file
public class LoadPLY implements MeshLoader {
	@Override
	public String getEnglishName() {
		return "3D scanner data (CSV)";
	}
	
	@Override
	public String[] getValidExtensions() {
		return new String[]{"csv"};
	}

	@Override
	public Mesh load(BufferedInputStream inputStream) throws Exception {
		Mesh model = new Mesh();
		
		model.renderStyle = GL2.GL_POINTS;

		BufferedReader br = new BufferedReader(new InputStreamReader(inputStream,"UTF-8"));
		String line;
		// eat the first line that says "X,Y,Z,SIGNAL_STRENGTH"
		line = br.readLine();
		// read the vertexes
		while( ( line = br.readLine() ) != null ) {
			line = line.trim();
			String[] tokens = line.split(",");
			float x=Float.parseFloat(tokens[0]);
			float y=Float.parseFloat(tokens[1]);
			float z=Float.parseFloat(tokens[2]);
			//float strength=Float.parseFloat(tokens[3]);
			model.addVertex(x,y,z);
		}
		
		return model;
	}

}
