
import java.awt.Color;
import java.awt.Graphics;


public class CafeWall {
	
	
	static int posx = 50;
	static int posy = 70;
	static int pairs = 5;
	static int bsize = 30;
	static int mortar = 2;
	static int ill = 10;
	static boolean alt = true;
	static int count = 0;
	
	

	

	public static void CafeWall(Graphics g,int posx,int posy,int pairs,int bsize, int ill) {
			
		
		count++;
		//for each pair creates twice as many rows
		for (int r = 0; r < 2*pairs; r++) {
			//after first row seperate by mortar
			// and shift the alternately by the illusion variable
			if (r >= 1) {posy = posy + mortar;
				if (alt == true) {
				posx = posx + ill;
				  alt = false;
				}	
				else if (alt == false) {
					posx = posx - ill;
					alt = true;
					}
			}
				for (int p = 0; p < pairs; p++) {
		
					g.setColor(Color.BLACK);
					g.fillRect(posx + 2 * p * bsize, posy + r * bsize, bsize, bsize);
					g.setColor(Color.BLUE);
					g.drawLine(posx + 2 * p * bsize, posy + r * bsize,
							posx + bsize + 2 * p * bsize, posy + bsize + r * bsize);
					g.drawLine(posx + bsize + 2 * p * bsize, posy + r * bsize,
							posx + 2 * p * bsize, posy + bsize + r * bsize);	
					g.setColor(Color.WHITE);
					g.fillRect(posx + bsize + 2 * p * bsize, posy + r * bsize, bsize, bsize);
				
				}
		}
		alt = true;
	}
		public static void main(String[] args) {
			

			DrawingPanel panel = new DrawingPanel(650, 400);
			Graphics g = panel.getGraphics();
			g.setColor(Color.DARK_GRAY);
			g.fillRect(0, 0, 650, 400);
			
			//upper right row
			for (int p = 0; p < 4; p++) {
				g.setColor(Color.BLACK);
				g.fillRect(0 + 2 * p * 20, 0, 20, 20);
				g.setColor(Color.BLUE);
				g.drawLine(0 + 2 * p * 20, 0,
					0 + 20 + 2 * p * 20, 0 + 20);
				g.drawLine(0 + 20 + 2 * p * 20, 0,
					0 + 2 * p * 20, 0 + 20);	
				g.setColor(Color.WHITE);
				g.fillRect(0 + 20 + 2 * p * 20, 0, 20, 20);
			}		
			
			//Midleft row
			for (int p = 0; p < pairs; p++) {				
				g.setColor(Color.BLACK);
				g.fillRect(posx + 2 * p * bsize, posy, bsize, bsize);
				g.setColor(Color.BLUE);
				g.drawLine(posx + 2 * p * bsize, posy,
						posx + bsize + 2 * p * bsize, posy + bsize);
				g.drawLine(posx + bsize + 2 * p * bsize, posy,
						posx + 2 * p * bsize, posy + bsize);	
				g.setColor(Color.WHITE);
				g.fillRect(posx + bsize + 2 * p * bsize, posy, bsize, bsize);
			
			}
			
									
			//the four real deal grids
			//cafewall(graphics, position x, position y, #pairs, boxsize, offset)
			
			CafeWall(g,10, 150, 4, 25, 0);
			CafeWall(g,250,200,3,25,10);
			CafeWall(g,425,180,5,20,10);
			CafeWall(g,400,20,2,35,35);
			
			
		}
}
